package com.hookflow.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything needed to send one attempt of a delivery.
 *
 * @param webhookId      sent as the {@code webhook-id} header; the same on every retry, so receivers can drop duplicates
 * @param eventCreatedAt when the event was accepted, used to measure end-to-end delivery time
 */
public record WebhookRequest(
		UUID deliveryId,
		int attemptNumber,
		String url,
		String signingSecret,
		String webhookId,
		String body,
		Instant eventCreatedAt) {
}
