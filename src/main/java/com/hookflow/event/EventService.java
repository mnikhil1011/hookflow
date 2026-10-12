package com.hookflow.event;

import java.util.Optional;
import java.util.UUID;

import com.hookflow.common.InvalidRequestException;
import com.hookflow.common.NotFoundException;
import com.hookflow.common.PayloadTooLargeException;
import com.hookflow.delivery.DeliveryMetrics;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class EventService {

	static final int MAX_PAYLOAD_CHARS = 256 * 1024;
	static final int MAX_IDEMPOTENCY_KEY_CHARS = 255;

	private final EventRepository events;
	private final DeliveryMetrics metrics;

	public EventService(EventRepository events, DeliveryMetrics metrics) {
		this.events = events;
		this.metrics = metrics;
	}

	/**
	 * Stores an event for delivery. Storing is all that happens here: the outbox relay publishes the
	 * event to Kafka afterwards, so an event that was accepted can never be lost before it is queued.
	 *
	 * @param idempotencyKey optional; a repeated key returns the event stored the first time
	 */
	public Event accept(UUID tenantId, String eventType, String payloadJson, String idempotencyKey) {
		if (payloadJson.length() > MAX_PAYLOAD_CHARS) {
			throw new PayloadTooLargeException("The payload must be at most 256 KB");
		}
		if (idempotencyKey == null) {
			return save(new Event(tenantId, eventType, payloadJson, null));
		}
		if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_CHARS) {
			throw new InvalidRequestException("The Idempotency-Key header must be at most 255 characters");
		}

		Optional<Event> existing = events.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
		if (existing.isPresent()) {
			return existing.get();
		}
		try {
			return save(new Event(tenantId, eventType, payloadJson, idempotencyKey));
		}
		catch (DataIntegrityViolationException e) {
			// Another request with the same key was saved between our check and our insert.
			return events.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).orElseThrow(() -> e);
		}
	}

	public Event get(UUID tenantId, UUID eventId) {
		return events.findByIdAndTenantId(eventId, tenantId)
				.orElseThrow(() -> new NotFoundException("Event not found"));
	}

	private Event save(Event event) {
		Event saved = events.saveAndFlush(event);
		metrics.eventAccepted();
		return saved;
	}

}
