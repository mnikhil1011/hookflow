package com.hookflow.delivery;

import java.util.List;
import java.util.UUID;

import com.hookflow.common.ConflictException;
import com.hookflow.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the API can do with deliveries: look them up and replay dead ones. */
@Service
public class DeliveryService {

	private final DeliveryRepository deliveries;
	private final DeliveryAttemptRepository attempts;

	public DeliveryService(DeliveryRepository deliveries, DeliveryAttemptRepository attempts) {
		this.deliveries = deliveries;
		this.attempts = attempts;
	}

	public Page<Delivery> search(UUID tenantId, UUID endpointId, DeliveryStatus status, int page, int size) {
		PageRequest newestFirst = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		return deliveries.search(tenantId, endpointId, status, newestFirst);
	}

	public Delivery get(UUID tenantId, UUID deliveryId) {
		return deliveries.findByIdAndTenantId(deliveryId, tenantId)
				.orElseThrow(() -> new NotFoundException("Delivery not found"));
	}

	public List<DeliveryAttempt> attemptsOf(UUID deliveryId) {
		return attempts.findByDeliveryIdOrderByAttemptNumber(deliveryId);
	}

	/** Makes a dead delivery due again. The scheduler picks it up within its polling interval. */
	@Transactional
	public Delivery replay(UUID tenantId, UUID deliveryId) {
		Delivery delivery = get(tenantId, deliveryId);
		if (delivery.getStatus() != DeliveryStatus.DEAD) {
			throw new ConflictException("Only a DEAD delivery can be replayed; this one is " + delivery.getStatus());
		}
		delivery.replay();
		return delivery;
	}

}
