package com.hookflow.event;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {

	Optional<Event> findByIdAndTenantId(UUID id, UUID tenantId);

	Optional<Event> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

}
