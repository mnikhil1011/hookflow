package com.hookflow.delivery;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import com.hookflow.config.HookflowProperties;
import com.hookflow.flowcontrol.FlowController;
import com.hookflow.flowcontrol.FlowController.SlotDecision;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides what happens to each delivery taken from Kafka: send it now, or let it wait.
 * <p>
 * The Kafka listener thread never waits for a receiver. Each delivery is handled on its own virtual
 * thread, which is cheap enough that thousands can sit waiting for HTTP responses at once. A delivery
 * with no free slot is parked in Redis instead of waiting, so a slow endpoint cannot hold up the
 * other endpoints on its partition.
 */
@Component
public class DeliveryDispatcher {

	private static final Logger log = LoggerFactory.getLogger(DeliveryDispatcher.class);

	private final FlowController flowController;
	private final DeliveryWorker worker;
	private final DeliveryMetrics metrics;

	/** Caps the deliveries this instance handles at once. When it is full, the listener stops taking work. */
	private final Semaphore capacity;
	private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();

	public DeliveryDispatcher(FlowController flowController, DeliveryWorker worker, DeliveryMetrics metrics,
			HookflowProperties properties) {
		this.flowController = flowController;
		this.worker = worker;
		this.metrics = metrics;
		this.capacity = new Semaphore(properties.delivery().maxInFlight());
	}

	/** Called by the Kafka listener. Blocks only while this instance is at full capacity. */
	public void dispatch(UUID endpointId, UUID deliveryId) throws InterruptedException {
		capacity.acquire();
		virtualThreads.execute(() -> {
			try {
				sendIfSlotIsFree(endpointId, deliveryId);
			}
			finally {
				capacity.release();
			}
		});
	}

	private void sendIfSlotIsFree(UUID endpointId, UUID deliveryId) {
		try {
			SlotDecision decision = flowController.acquire(endpointId, deliveryId);
			if (decision == SlotDecision.GRANTED) {
				sendThenContinue(endpointId, deliveryId);
			}
			else if (decision == SlotDecision.PARKED) {
				// Nothing more to do: the delivery now waits in Redis and is handed a slot when one frees
				// up. If that somehow never happens, its lease runs out and the scheduler queues it again.
				metrics.parked();
			}
		}
		catch (RuntimeException e) {
			// Nothing is lost: the delivery's lease runs out and the scheduler queues it again.
			log.error("Handling delivery {} for endpoint {} failed unexpectedly", deliveryId, endpointId, e);
		}
	}

	/**
	 * Sends a delivery, then keeps going with whatever the flow controller hands over for the same
	 * endpoint. This hand-off is how waiting deliveries drain without another trip through Kafka.
	 */
	private void sendThenContinue(UUID endpointId, UUID firstDeliveryId) {
		UUID deliveryId = firstDeliveryId;
		while (deliveryId != null) {
			List<UUID> handedOver = worker.attempt(endpointId, deliveryId);
			// This thread takes the first one. Any others already hold a slot, so they run in parallel.
			handedOver.stream().skip(1).forEach(other -> sendInParallel(endpointId, other));
			deliveryId = handedOver.isEmpty() ? null : handedOver.getFirst();
		}
	}

	private void sendInParallel(UUID endpointId, UUID deliveryId) {
		virtualThreads.execute(() -> {
			capacity.acquireUninterruptibly();
			try {
				sendThenContinue(endpointId, deliveryId);
			}
			catch (RuntimeException e) {
				log.error("Sending delivery {} for endpoint {} failed unexpectedly", deliveryId, endpointId, e);
			}
			finally {
				capacity.release();
			}
		});
	}

	@PreDestroy
	void shutdown() {
		virtualThreads.shutdown();
	}

}
