package com.hookflow.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.hookflow.IntegrationTest;
import com.hookflow.TestProperties;
import com.hookflow.config.HookflowProperties.FlowControl.Mode;
import com.hookflow.flowcontrol.FlowController.Signal;
import com.hookflow.flowcontrol.FlowController.SlotDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Runs the real Lua scripts against a real Redis. Each test uses a fresh endpoint ID, so tests cannot affect each other. */
class RedisFlowControllerTest extends IntegrationTest {

	@Autowired
	private StringRedisTemplate redis;

	private UUID endpointId;

	@BeforeEach
	void useAFreshEndpoint() {
		endpointId = UUID.randomUUID();
	}

	@Test
	void neverGrantsMoreSlotsThanTheLimitEvenUnderContention() throws Exception {
		FlowController controller = controller(Mode.FIXED, 10);
		List<Callable<SlotDecision>> attempts = IntStream.range(0, 50)
				.<Callable<SlotDecision>>mapToObj(i -> () -> controller.acquire(endpointId, UUID.randomUUID()))
				.toList();

		List<SlotDecision> decisions;
		try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<SlotDecision>> futures = threads.invokeAll(attempts);
			decisions = futures.stream().map(RedisFlowControllerTest::resultOf).toList();
		}

		assertThat(decisions).filteredOn(decision -> decision == SlotDecision.GRANTED).hasSize(10);
		assertThat(decisions).filteredOn(decision -> decision == SlotDecision.PARKED).hasSize(40);
		assertThat(controller.state(endpointId).inFlight()).isEqualTo(10);
		assertThat(controller.state(endpointId).waiting()).isEqualTo(40);
	}

	@Test
	void reportsADeliveryThatAlreadyHoldsASlotAsDuplicate() {
		FlowController controller = controller(Mode.FIXED, 5);
		UUID deliveryId = UUID.randomUUID();

		assertThat(controller.acquire(endpointId, deliveryId)).isEqualTo(SlotDecision.GRANTED);
		assertThat(controller.acquire(endpointId, deliveryId)).isEqualTo(SlotDecision.DUPLICATE);
	}

	@Test
	void freesASlotWhenItsLeaseExpires() throws InterruptedException {
		FlowController controller = controller(Mode.FIXED, 1);

		assertThat(controller.acquire(endpointId, UUID.randomUUID())).isEqualTo(SlotDecision.GRANTED);
		assertThat(controller.acquire(endpointId, UUID.randomUUID())).isEqualTo(SlotDecision.PARKED);

		// The holder never releases, as if its worker had crashed. The test lease is 300 ms.
		Thread.sleep(400);

		assertThat(controller.acquire(endpointId, UUID.randomUUID())).isEqualTo(SlotDecision.GRANTED);
	}

	@Test
	void handsAFreedSlotToTheDeliveryThatWaitedLongest() {
		FlowController controller = controller(Mode.FIXED, 1);
		UUID sending = UUID.randomUUID();
		UUID waitingFirst = UUID.randomUUID();
		UUID waitingSecond = UUID.randomUUID();
		controller.acquire(endpointId, sending);
		controller.acquire(endpointId, waitingFirst);
		controller.acquire(endpointId, waitingSecond);

		List<UUID> handedOver = controller.release(endpointId, sending, Signal.OK);

		assertThat(handedOver).containsExactly(waitingFirst);
		assertThat(controller.state(endpointId).inFlight()).isEqualTo(1);
		assertThat(controller.state(endpointId).waiting()).isEqualTo(1);
	}

	@Test
	void raisesTheLimitSlowlyOnSuccess() {
		FlowController controller = controller(Mode.ADAPTIVE, 4);

		completeOneRequest(controller, Signal.OK);
		assertThat(controller.state(endpointId).limit()).isCloseTo(4.25, within(0.001));

		// Three more successes complete one "round" of four, which adds up to about one extra slot.
		completeOneRequest(controller, Signal.OK);
		completeOneRequest(controller, Signal.OK);
		completeOneRequest(controller, Signal.OK);
		assertThat(controller.state(endpointId).limit()).isCloseTo(4.92, within(0.01));
	}

	@Test
	void halvesTheLimitOnFailureButOnlyOncePerCooldown() throws InterruptedException {
		FlowController controller = controller(Mode.ADAPTIVE, 8);

		completeOneRequest(controller, Signal.CONGESTED);
		assertThat(controller.state(endpointId).limit()).isEqualTo(4.0);

		// A second failure straight away is part of the same incident, so it changes nothing.
		completeOneRequest(controller, Signal.CONGESTED);
		assertThat(controller.state(endpointId).limit()).isEqualTo(4.0);

		// After the cooldown (100 ms in tests) a failure counts again.
		Thread.sleep(150);
		completeOneRequest(controller, Signal.CONGESTED);
		assertThat(controller.state(endpointId).limit()).isEqualTo(2.0);
	}

	@Test
	void pausesAnEndpointThatFailsAtItsMinimumLimit() throws InterruptedException {
		FlowController controller = controller(Mode.ADAPTIVE, 1);

		completeOneRequest(controller, Signal.CONGESTED);

		assertThat(controller.state(endpointId).paused()).isTrue();
		assertThat(controller.acquire(endpointId, UUID.randomUUID())).isEqualTo(SlotDecision.PARKED);

		// The test's maximum pause is 200 ms. After it, one probe request is let through.
		Thread.sleep(300);
		assertThat(controller.state(endpointId).paused()).isFalse();
		assertThat(controller.acquire(endpointId, UUID.randomUUID())).isEqualTo(SlotDecision.GRANTED);
	}

	@Test
	void aSuccessEndsThePauseAndDrainsWaitingDeliveries() throws InterruptedException {
		FlowController controller = controller(Mode.ADAPTIVE, 1);
		completeOneRequest(controller, Signal.CONGESTED);
		UUID waiting = UUID.randomUUID();
		controller.acquire(endpointId, waiting);
		Thread.sleep(300);

		UUID probe = UUID.randomUUID();
		controller.acquire(endpointId, probe);
		List<UUID> handedOver = controller.release(endpointId, probe, Signal.OK);

		assertThat(handedOver).containsExactly(waiting);
		assertThat(controller.state(endpointId).paused()).isFalse();
	}

	@Test
	void neverChangesTheLimitInFixedMode() {
		FlowController controller = controller(Mode.FIXED, 5);

		completeOneRequest(controller, Signal.CONGESTED);
		completeOneRequest(controller, Signal.OK);

		assertThat(controller.state(endpointId).limit()).isEqualTo(5.0);
		assertThat(controller.state(endpointId).paused()).isFalse();
	}

	@Test
	void aSkippedDeliveryFreesItsSlotWithoutChangingTheLimit() {
		FlowController controller = controller(Mode.ADAPTIVE, 4);

		completeOneRequest(controller, Signal.NONE);

		assertThat(controller.state(endpointId).limit()).isEqualTo(4.0);
		assertThat(controller.state(endpointId).inFlight()).isZero();
	}

	private FlowController controller(Mode mode, int startingLimit) {
		return new RedisFlowController(redis, TestProperties.flowControl(mode, startingLimit), new SimpleMeterRegistry());
	}

	/** Takes a slot and gives it back with the given outcome, like one request to the endpoint. */
	private void completeOneRequest(FlowController controller, Signal signal) {
		UUID deliveryId = UUID.randomUUID();
		assertThat(controller.acquire(endpointId, deliveryId)).isEqualTo(SlotDecision.GRANTED);
		controller.release(endpointId, deliveryId, signal);
	}

	private static SlotDecision resultOf(Future<SlotDecision> future) {
		try {
			return future.get();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
