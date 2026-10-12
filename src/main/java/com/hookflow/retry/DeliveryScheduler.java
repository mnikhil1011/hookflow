package com.hookflow.retry;

import java.util.List;
import java.util.UUID;

import com.hookflow.config.HookflowProperties;
import com.hookflow.config.KafkaConfig;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Queues every delivery that is due, by publishing its ID to Kafka.
 * <p>
 * "Due" means its {@code next_attempt_at} has passed. That one rule covers four cases:
 * <ul>
 * <li>a new delivery, which is due as soon as fan-out creates it;</li>
 * <li>a failed delivery whose retry time has come;</li>
 * <li>a delivery whose worker crashed, once the worker's lease has run out;</li>
 * <li>a delivery that waited for a slot longer than the park timeout.</li>
 * </ul>
 * Because the work list lives in Postgres, losing Kafka or Redis data only delays deliveries.
 */
@Component
public class DeliveryScheduler {

	private record DueDelivery(UUID id, UUID endpointId) {
	}

	private final JdbcClient jdbc;
	private final KafkaTemplate<String, String> kafka;
	private final int batchSize;
	private final long leaseSeconds;

	public DeliveryScheduler(JdbcClient jdbc, KafkaTemplate<String, String> kafka, HookflowProperties properties) {
		this.jdbc = jdbc;
		this.kafka = kafka;
		this.batchSize = properties.polling().batchSize();
		this.leaseSeconds = properties.delivery().attemptLease().toSeconds();
	}

	@Scheduled(fixedDelayString = "${hookflow.polling.interval}")
	public void queueDueDeliveries() {
		int queued;
		do {
			queued = queueBatch();
		}
		while (queued == batchSize);
	}

	private int queueBatch() {
		// One statement claims the rows and pushes their next attempt time forward, so a delivery is
		// not queued again while it is on its way to a worker. If the message below gets lost, that
		// lease simply runs out and the delivery becomes due again.
		// SKIP LOCKED lets several application instances run this job without claiming the same rows.
		List<DueDelivery> due = jdbc.sql("""
				UPDATE deliveries
				SET next_attempt_at = now() + make_interval(secs => :leaseSeconds)
				WHERE id IN (
				    SELECT id
				    FROM deliveries
				    WHERE next_attempt_at <= now()
				    ORDER BY next_attempt_at
				    LIMIT :limit
				    FOR UPDATE SKIP LOCKED)
				RETURNING id, endpoint_id
				""")
				.param("leaseSeconds", leaseSeconds)
				.param("limit", batchSize)
				.query((row, rowNumber) -> new DueDelivery(row.getObject("id", UUID.class), row.getObject("endpoint_id", UUID.class)))
				.list();

		// Keyed by endpoint, so one endpoint's deliveries stay on one partition.
		due.forEach(delivery -> kafka.send(KafkaConfig.DELIVERIES_TOPIC, delivery.endpointId().toString(), delivery.id().toString()));
		return due.size();
	}

}
