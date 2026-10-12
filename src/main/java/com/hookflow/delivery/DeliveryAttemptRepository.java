package com.hookflow.delivery;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {

	List<DeliveryAttempt> findByDeliveryIdOrderByAttemptNumber(UUID deliveryId);

}
