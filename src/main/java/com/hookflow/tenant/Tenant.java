package com.hookflow.tenant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A customer of Hookflow. It sends events through the API. */
@Entity
@Table(name = "tenants")
public class Tenant {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "api_key_hash", nullable = false)
	private String apiKeyHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	protected Tenant() {
		// for JPA
	}

	public Tenant(String name, String apiKeyHash) {
		this.name = name;
		this.apiKeyHash = apiKeyHash;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
