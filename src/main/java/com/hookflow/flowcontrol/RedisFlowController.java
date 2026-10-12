package com.hookflow.flowcontrol;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hookflow.config.HookflowProperties.FlowControl;
import com.hookflow.config.HookflowProperties.FlowControl.Mode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Keeps each endpoint's limit and slots in Redis, so every application instance shares them.
 * <p>
 * In adaptive mode the limit follows AIMD, the rule TCP uses for congestion control: each success
 * raises it a little, each failure halves it. In fixed mode the limit never changes.
 * <p>
 * The logic lives in two Lua scripts because Redis runs a script as one uninterrupted step. Two
 * workers can therefore never both take the last slot.
 */
public class RedisFlowController implements FlowController {

	private static final RedisScript<Long> ACQUIRE = RedisScript.of(new ClassPathResource("lua/acquire.lua"), Long.class);

	@SuppressWarnings("rawtypes")
	private static final RedisScript<List> RELEASE = RedisScript.of(new ClassPathResource("lua/release.lua"), List.class);

	private final StringRedisTemplate redis;
	private final FlowControl config;
	private final MeterRegistry meterRegistry;
	private final boolean adaptive;
	private final int startingLimit;

	/** The latest limit seen per endpoint, published as a gauge so it can be graphed. */
	private final Map<UUID, Double> latestLimits = new ConcurrentHashMap<>();

	public RedisFlowController(StringRedisTemplate redis, FlowControl config, MeterRegistry meterRegistry) {
		this.redis = redis;
		this.config = config;
		this.meterRegistry = meterRegistry;
		this.adaptive = config.mode() == Mode.ADAPTIVE;
		this.startingLimit = adaptive ? config.initialLimit() : config.fixedLimit();
	}

	@Override
	public SlotDecision acquire(UUID endpointId, UUID deliveryId) {
		Long result = redis.execute(ACQUIRE, keys(endpointId),
				deliveryId.toString(),
				String.valueOf(config.slotLease().toMillis()),
				String.valueOf(startingLimit),
				adaptive ? "1" : "0",
				String.valueOf(config.maxWaiting()));
		return switch (result.intValue()) {
			case 1 -> SlotDecision.GRANTED;
			case 2 -> SlotDecision.DUPLICATE;
			default -> SlotDecision.PARKED;
		};
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<UUID> release(UUID endpointId, UUID deliveryId, Signal signal) {
		List<String> result = redis.execute(RELEASE, keys(endpointId),
				deliveryId.toString(),
				signal.name(),
				String.valueOf(config.slotLease().toMillis()),
				String.valueOf(startingLimit),
				adaptive ? "1" : "0",
				String.valueOf(config.minLimit()),
				String.valueOf(config.maxLimit()),
				String.valueOf(config.decreaseFactor()),
				String.valueOf(config.decreaseCooldown().toMillis()),
				String.valueOf(config.maxPause().toMillis()));

		// The script returns the current limit first, then the deliveries it handed a slot to.
		publishLimit(endpointId, Double.parseDouble(result.getFirst()));
		return result.stream().skip(1).map(UUID::fromString).toList();
	}

	@Override
	public FlowState state(UUID endpointId) {
		List<String> keys = keys(endpointId);
		// The scripts use the Redis clock, so read the same clock here.
		long now = redis.execute((RedisCallback<Long>) connection -> connection.serverCommands().time());

		Object storedLimit = redis.opsForHash().get(keys.get(0), "limit");
		Object blockedUntil = redis.opsForHash().get(keys.get(0), "blocked_until");
		Long inFlight = redis.opsForZSet().count(keys.get(1), now, Double.POSITIVE_INFINITY);
		Long waiting = redis.opsForZSet().zCard(keys.get(2));

		double limit = adaptive && storedLimit != null ? Double.parseDouble(storedLimit.toString()) : startingLimit;
		boolean paused = blockedUntil != null && Double.parseDouble(blockedUntil.toString()) > now;
		return new FlowState(limit, inFlight == null ? 0 : inFlight, waiting == null ? 0 : waiting, paused);
	}

	/**
	 * The three Redis keys of one endpoint: its state hash, its in-flight set and its waiting set.
	 * The braces are a Redis Cluster hash tag: they keep the keys on one node, which a script needs.
	 */
	private static List<String> keys(UUID endpointId) {
		String prefix = "hf:ep:{" + endpointId + "}:";
		return List.of(prefix + "state", prefix + "inflight", prefix + "waiting");
	}

	private void publishLimit(UUID endpointId, double limit) {
		boolean firstTimeSeen = latestLimits.put(endpointId, limit) == null;
		if (firstTimeSeen) {
			Gauge.builder("hookflow.flowcontrol.limit", latestLimits, limits -> limits.getOrDefault(endpointId, Double.NaN))
					.description("Current concurrency limit of an endpoint")
					.tag("endpoint", endpointId.toString())
					.register(meterRegistry);
		}
	}

}
