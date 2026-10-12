package com.hookflow.event;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.hookflow.config.HookflowProperties;
import com.hookflow.config.KafkaConfig;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes saved events to Kafka. This is the "transactional outbox" pattern.
 * <p>
 * Saving to Postgres and publishing to Kafka cannot happen in one transaction, and the application
 * could crash between the two. So the API only saves, and this job publishes whatever has not been
 * published yet. A crash at any point delays an event but never loses it. An event can be published
 * twice, which is harmless because fan-out ignores events it has already handled.
 */
@Component
public class OutboxRelay {

	private record PendingEvent(UUID id, UUID tenantId) {
	}

	private final JdbcClient jdbc;
	private final KafkaTemplate<String, String> kafka;
	private final TransactionTemplate transaction;
	private final int batchSize;

	public OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate transaction,
			HookflowProperties properties) {
		this.jdbc = jdbc;
		this.kafka = kafka;
		this.transaction = transaction;
		this.batchSize = properties.polling().batchSize();
	}

	@Scheduled(fixedDelayString = "${hookflow.polling.interval}")
	public void publishPendingEvents() {
		int published;
		do {
			published = transaction.execute(status -> publishBatch());
		}
		while (published == batchSize);
	}

	private int publishBatch() {
		// SKIP LOCKED lets several application instances run this job without publishing the same rows.
		List<PendingEvent> pending = jdbc.sql("""
				SELECT id, tenant_id
				FROM events
				WHERE published_at IS NULL
				ORDER BY created_at
				LIMIT :limit
				FOR UPDATE SKIP LOCKED
				""")
				.param("limit", batchSize)
				.query((row, rowNumber) -> new PendingEvent(row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class)))
				.list();
		if (pending.isEmpty()) {
			return 0;
		}

		CompletableFuture<?>[] sends = pending.stream()
				.map(event -> kafka.send(KafkaConfig.EVENTS_TOPIC, event.tenantId().toString(), event.id().toString()))
				.toArray(CompletableFuture[]::new);
		// Wait until Kafka has confirmed every message. A failure rolls back, and the batch is retried.
		CompletableFuture.allOf(sends).orTimeout(10, TimeUnit.SECONDS).join();

		jdbc.sql("UPDATE events SET published_at = now() WHERE id IN (:ids)")
				.param("ids", pending.stream().map(PendingEvent::id).toList())
				.update();
		return pending.size();
	}

}
