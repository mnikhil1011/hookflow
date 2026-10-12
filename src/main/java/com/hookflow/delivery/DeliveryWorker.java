package com.hookflow.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.hookflow.flowcontrol.FlowController;
import com.hookflow.flowcontrol.FlowController.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Carries out one attempt of one delivery, for which the caller already holds a slot. */
@Component
public class DeliveryWorker {

	private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

	private final DeliveryLifecycle lifecycle;
	private final WebhookSender sender;
	private final FlowController flowController;
	private final DeliveryMetrics metrics;

	public DeliveryWorker(DeliveryLifecycle lifecycle, WebhookSender sender, FlowController flowController,
			DeliveryMetrics metrics) {
		this.lifecycle = lifecycle;
		this.sender = sender;
		this.flowController = flowController;
		this.metrics = metrics;
	}

	/**
	 * Sends the delivery, records the result and frees the slot.
	 *
	 * @return deliveries for the same endpoint that were handed a slot and must be sent next
	 */
	public List<UUID> attempt(UUID endpointId, UUID deliveryId) {
		Signal signal = Signal.NONE;
		try {
			Optional<WebhookRequest> request = lifecycle.prepareAttempt(deliveryId);
			if (request.isPresent()) {
				SendResult result = sender.send(request.get());
				AttemptOutcome outcome = AttemptOutcome.of(result);
				signal = outcome.flowSignal();
				DeliveryStatus status = lifecycle.recordResult(request.get(), result, outcome);
				recordMetrics(endpointId, request.get(), result, outcome, status);
				log.debug("Delivery {} attempt {}: {} (status {}, error {})", deliveryId,
						request.get().attemptNumber(), outcome, result.status(), result.error());
			}
		}
		catch (RuntimeException e) {
			// Nothing is lost: the delivery's lease expires and the scheduler queues it again.
			log.error("Attempt for delivery {} failed unexpectedly", deliveryId, e);
		}
		// Always free the slot, whatever happened above.
		return flowController.release(endpointId, deliveryId, signal);
	}

	private void recordMetrics(UUID endpointId, WebhookRequest request, SendResult result, AttemptOutcome outcome,
			DeliveryStatus status) {
		metrics.attempt(outcome, result.duration());
		if (status == DeliveryStatus.SUCCEEDED) {
			metrics.delivered(endpointId, Duration.between(request.eventCreatedAt(), Instant.now()));
		}
		else if (status == DeliveryStatus.DEAD) {
			metrics.dead();
		}
	}

}
