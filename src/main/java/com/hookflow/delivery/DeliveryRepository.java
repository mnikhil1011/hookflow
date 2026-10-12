package com.hookflow.delivery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

	Optional<Delivery> findByIdAndTenantId(UUID id, UUID tenantId);

	List<Delivery> findByEventIdOrderByCreatedAt(UUID eventId);

	/** Lists a tenant's deliveries. A null filter means "any". */
	@Query("""
			select d from Delivery d
			where d.tenantId = :tenantId
			  and (:endpointId is null or d.endpointId = :endpointId)
			  and (:status is null or d.status = :status)
			""")
	Page<Delivery> search(@Param("tenantId") UUID tenantId, @Param("endpointId") UUID endpointId,
			@Param("status") DeliveryStatus status, Pageable pageable);

}
