package com.hookflow.fanout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hookflow.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Turns each event into one delivery per endpoint.
 * <p>
 * Kafka may deliver the same message twice, so this must be safe to repeat. The unique constraint on
 * (event_id, endpoint_id) together with ON CONFLICT DO NOTHING makes a second run create nothing.
 * The new rows are due immediately, and the delivery scheduler queues them from there.
 * <p>
 * Messages are handled a batch at a time with one SQL statement. One commit per batch is far cheaper
 * than one per event, because every commit waits for the database to flush to disk.
 */
@Component
public class FanoutConsumer {

	private static final Logger log = LoggerFactory.getLogger(FanoutConsumer.class);

	private final JdbcClient jdbc;

	public FanoutConsumer(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@KafkaListener(topics = KafkaConfig.EVENTS_TOPIC, groupId = "hookflow-fanout", batch = "true", concurrency = "3")
	public void onEvents(List<String> messages) {
		List<UUID> eventIds = parseEventIds(messages);
		if (eventIds.isEmpty()) {
			return;
		}
		int created = jdbc.sql("""
				INSERT INTO deliveries (id, event_id, endpoint_id, tenant_id, status, next_attempt_at)
				SELECT gen_random_uuid(), ev.id, ep.id, ev.tenant_id, 'PENDING', now()
				FROM events ev
				JOIN endpoints ep ON ep.tenant_id = ev.tenant_id
				WHERE ev.id IN (:eventIds) AND ep.enabled
				ON CONFLICT (event_id, endpoint_id) DO NOTHING
				""")
				.param("eventIds", eventIds)
				.update();
		log.debug("{} events fanned out to {} new deliveries", eventIds.size(), created);
	}

	private static List<UUID> parseEventIds(List<String> messages) {
		List<UUID> eventIds = new ArrayList<>();
		for (String message : messages) {
			try {
				eventIds.add(UUID.fromString(message));
			}
			catch (IllegalArgumentException e) {
				log.warn("Ignoring a message that is not an event ID: {}", message);
			}
		}
		return eventIds;
	}

}
