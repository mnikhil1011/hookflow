package com.hookflow.delivery;

import java.time.Duration;
import java.time.Instant;

/**
 * What happened when a webhook was sent.
 *
 * @param status          the HTTP status, or null when no response arrived
 * @param error           why no response arrived (TIMEOUT, CONNECTION_ERROR), or null when one did
 * @param responseSnippet the start of the response body, kept for debugging
 */
public record SendResult(Instant startedAt, Duration duration, Integer status, String error, String responseSnippet) {

	public static SendResult responded(Instant startedAt, Duration duration, int status, String responseSnippet) {
		return new SendResult(startedAt, duration, status, null, responseSnippet);
	}

	public static SendResult failed(Instant startedAt, Duration duration, String error) {
		return new SendResult(startedAt, duration, null, error, null);
	}

}
