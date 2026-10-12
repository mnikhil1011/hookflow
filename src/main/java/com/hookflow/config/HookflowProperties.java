package com.hookflow.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All of Hookflow's own settings, bound from the {@code hookflow.*} keys in application.yaml.
 */
@ConfigurationProperties("hookflow")
public record HookflowProperties(
		String adminToken,
		boolean allowInsecureUrls,
		Delivery delivery,
		FlowControl flowControl,
		Polling polling) {

	/**
	 * @param connectTimeout how long to wait for a TCP connection to the receiver
	 * @param requestTimeout how long to wait for the receiver's response
	 * @param attemptLease   how long a queued delivery has to finish (including any wait for a slot)
	 *                       before the scheduler queues it again
	 * @param maxInFlight    the most requests one application instance sends at the same time
	 * @param retryDelays    wait before attempt 2, 3, ... (so max attempts = size + 1)
	 * @param retryJitter    random variation applied to each wait; 0.2 means up to 20% either way
	 */
	public record Delivery(
			Duration connectTimeout,
			Duration requestTimeout,
			Duration attemptLease,
			int maxInFlight,
			List<Duration> retryDelays,
			double retryJitter) {
	}

	/**
	 * @param mode             adaptive (AIMD), fixed (constant limit) or none (no per-endpoint limit)
	 * @param fixedLimit       the limit used in fixed mode
	 * @param initialLimit     adaptive mode: limit for an endpoint with no history
	 * @param decreaseFactor   adaptive mode: multiplier applied to the limit on a failure signal
	 * @param decreaseCooldown adaptive mode: minimum time between two decreases
	 * @param slotLease        how long a slot is held before it is considered abandoned
	 * @param maxPause         longest pause for an endpoint that keeps failing at the minimum limit
	 * @param maxWaiting       most deliveries that may wait in Redis for one endpoint
	 */
	public record FlowControl(
			Mode mode,
			int fixedLimit,
			int initialLimit,
			int minLimit,
			int maxLimit,
			double decreaseFactor,
			Duration decreaseCooldown,
			Duration slotLease,
			Duration maxPause,
			int maxWaiting) {

		public enum Mode { ADAPTIVE, FIXED, NONE }
	}

	/**
	 * Settings shared by the two background jobs that move work from Postgres to Kafka.
	 *
	 * @param interval  pause between two runs
	 * @param batchSize rows handled per query
	 */
	public record Polling(Duration interval, int batchSize) {
	}

}
