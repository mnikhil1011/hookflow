package com.hookflow.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One thing that happened, sent in by a tenant. Hookflow delivers it to each of the tenant's endpoints. */
@Entity
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	/** The tenant's JSON payload, kept as text and stored in a JSONB column. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@Column(name = "idempotency_key")
	private String idempotencyKey;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	protected Event() {
		// for JPA
	}

	public Event(UUID tenantId, String eventType, String payload, String idempotencyKey) {
		this.tenantId = tenantId;
		this.eventType = eventType;
		this.payload = payload;
		this.idempotencyKey = idempotencyKey;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
