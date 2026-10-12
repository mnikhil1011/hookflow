package com.hookflow.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.hookflow.endpoint.Endpoint;
import com.hookflow.endpoint.EndpointRepository;
import com.hookflow.event.Event;
import com.hookflow.event.EventRepository;
import com.hookflow.retry.RetryPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The database side of a delivery attempt. Each method is one short transaction, so no transaction
 * is ever held open while waiting for a receiver to answer.
 */
@Service
public class DeliveryLifecycle {

	private final DeliveryRepository deliveries;
	private final DeliveryAttemptRepository attempts;
	private final EndpointRepository endpoints;
	private final EventRepository events;
	private final RetryPolicy retryPolicy;
	private final JsonMapper json;

	public DeliveryLifecycle(DeliveryRepository deliveries, DeliveryAttemptRepository attempts,
			EndpointRepository endpoints, EventRepository events, RetryPolicy retryPolicy, JsonMapper json) {
		this.deliveries = deliveries;
		this.attempts = attempts;
		this.endpoints = endpoints;
		this.events = events;
		this.retryPolicy = retryPolicy;
		this.json = json;
	}

	/**
	 * Gathers what is needed to send the delivery's next attempt. Nothing is written here, so the
	 * common case costs the database only reads.
	 *
	 * @return empty when there is nothing to send: the delivery is already finished, or its endpoint was disabled
	 */
	@Transactional
	public Optional<WebhookRequest> prepareAttempt(UUID deliveryId) {
		Delivery delivery = deliveries.findById(deliveryId).orElse(null);
		if (delivery == null || delivery.isFinished()) {
			return Optional.empty();
		}
		Endpoint endpoint = endpoints.findById(delivery.getEndpointId()).orElseThrow();
		if (!endpoint.isEnabled()) {
			delivery.markDead();
			return Optional.empty();
		}
		Event event = events.findById(delivery.getEventId()).orElseThrow();

		return Optional.of(new WebhookRequest(delivery.getId(), delivery.getAttemptCount() + 1, endpoint.getUrl(),
				endpoint.getSigningSecret(), event.getId().toString(), bodyOf(event), event.getCreatedAt()));
	}

	/**
	 * Saves the attempt and moves the delivery to its next state.
	 *
	 * @return the delivery's new status
	 */
	@Transactional
	public DeliveryStatus recordResult(WebhookRequest request, SendResult result, AttemptOutcome outcome) {
		attempts.save(new DeliveryAttempt(request.deliveryId(), request.attemptNumber(), result));

		Delivery delivery = deliveries.findById(request.deliveryId()).orElseThrow();
		delivery.countAttempt();
		switch (outcome) {
			case SUCCESS -> delivery.markSucceeded();
			case PERMANENT_FAILURE -> delivery.markDead();
			case RETRYABLE_FAILURE -> {
				if (retryPolicy.hasAttemptsLeft(delivery.getAttemptCount())) {
					Duration wait = retryPolicy.delayBefore(delivery.getAttemptCount() + 1);
					delivery.retryAt(Instant.now().plus(wait));
				}
				else {
					delivery.markDead();
				}
			}
		}
		return delivery.getStatus();
	}

	/** Builds the JSON body receivers get: the event's type, time and data. */
	private String bodyOf(Event event) {
		ObjectNode body = json.createObjectNode();
		body.put("type", event.getEventType());
		body.put("timestamp", event.getCreatedAt().toString());
		body.set("data", json.readTree(event.getPayload()));
		return json.writeValueAsString(body);
	}

}
