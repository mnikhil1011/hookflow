package com.hookflow.delivery;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The job of getting one event to one endpoint.
 * <p>
 * Rows are created by the fan-out consumer with plain SQL, so there is no public constructor.
 * {@code nextAttemptAt} is the time at which the delivery is next due. Whenever the delivery is handed
 * on (queued, or parked to wait for a slot) it is pushed into the future, which works as a lease: if
 * the work is lost along the way, the time passes and the delivery becomes due again. It is cleared
 * once the delivery is finished.
 */
@Entity
@Table(name = "deliveries")
public class Delivery {

	@Id
	private UUID id;

	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "endpoint_id", nullable = false)
	private UUID endpointId;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DeliveryStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at")
	private Instant nextAttemptAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Delivery() {
		// for JPA
	}

	public boolean isFinished() {
		return status == DeliveryStatus.SUCCEEDED || status == DeliveryStatus.DEAD;
	}

	public void countAttempt() {
		attemptCount++;
	}

	public void markSucceeded() {
		status = DeliveryStatus.SUCCEEDED;
		nextAttemptAt = null;
	}

	public void markDead() {
		status = DeliveryStatus.DEAD;
		nextAttemptAt = null;
	}

	public void retryAt(Instant when) {
		status = DeliveryStatus.RETRYING;
		nextAttemptAt = when;
	}

	/** Gives a dead delivery a fresh set of attempts, starting now. */
	public void replay() {
		status = DeliveryStatus.PENDING;
		attemptCount = 0;
		nextAttemptAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getEndpointId() {
		return endpointId;
	}

	public DeliveryStatus getStatus() {
		return status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
