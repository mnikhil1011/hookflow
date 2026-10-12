package com.hookflow.delivery;

import java.time.Instant;
import java.util.UUID;

/** How a delivery looks in API responses. */
public record DeliveryResponse(
		UUID id,
		UUID eventId,
		UUID endpointId,
		DeliveryStatus status,
		int attemptCount,
		Instant nextAttemptAt,
		Instant createdAt,
		Instant updatedAt) {

	public static DeliveryResponse of(Delivery delivery) {
		return new DeliveryResponse(delivery.getId(), delivery.getEventId(), delivery.getEndpointId(),
				delivery.getStatus(), delivery.getAttemptCount(), delivery.getNextAttemptAt(),
				delivery.getCreatedAt(), delivery.getUpdatedAt());
	}

}
