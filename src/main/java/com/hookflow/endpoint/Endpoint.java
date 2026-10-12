package com.hookflow.endpoint;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A URL owned by a tenant that Hookflow delivers webhooks to. */
@Entity
@Table(name = "endpoints")
public class Endpoint {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String url;

	@Column(name = "signing_secret", nullable = false)
	private String signingSecret;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	protected Endpoint() {
		// for JPA
	}

	public Endpoint(UUID tenantId, String url, String signingSecret) {
		this.tenantId = tenantId;
		this.url = url;
		this.signingSecret = signingSecret;
	}

	public void changeUrl(String url) {
		this.url = url;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getUrl() {
		return url;
	}

	public String getSigningSecret() {
		return signingSecret;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
