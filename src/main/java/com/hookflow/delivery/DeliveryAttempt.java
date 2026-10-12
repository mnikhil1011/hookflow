package com.hookflow.delivery;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A single HTTP request made for a delivery, and what came back. */
@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "delivery_id", nullable = false)
	private UUID deliveryId;

	@Column(name = "attempt_number", nullable = false)
	private int attemptNumber;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "duration_ms", nullable = false)
	private int durationMs;

	/** Null when no response arrived. */
	@Column(name = "response_status")
	private Integer responseStatus;

	/** Why no response arrived, such as TIMEOUT. Null when one did. */
	private String error;

	@Column(name = "response_snippet")
	private String responseSnippet;

	protected DeliveryAttempt() {
		// for JPA
	}

	public DeliveryAttempt(UUID deliveryId, int attemptNumber, SendResult result) {
		this.deliveryId = deliveryId;
		this.attemptNumber = attemptNumber;
		this.startedAt = result.startedAt();
		this.durationMs = (int) result.duration().toMillis();
		this.responseStatus = result.status();
		this.error = result.error();
		this.responseSnippet = result.responseSnippet();
	}

	public int getAttemptNumber() {
		return attemptNumber;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public int getDurationMs() {
		return durationMs;
	}

	public Integer getResponseStatus() {
		return responseStatus;
	}

	public String getError() {
		return error;
	}

	public String getResponseSnippet() {
		return responseSnippet;
	}

}
