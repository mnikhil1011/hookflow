package com.hookflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

	/** Carries the ID of every accepted event. Key: tenant ID. */
	public static final String EVENTS_TOPIC = "hookflow.events";

	/** Carries the ID of every delivery that is due. Key: endpoint ID. */
	public static final String DELIVERIES_TOPIC = "hookflow.deliveries";

	@Bean
	NewTopic eventsTopic() {
		return TopicBuilder.name(EVENTS_TOPIC).partitions(6).replicas(1).build();
	}

	@Bean
	NewTopic deliveriesTopic() {
		return TopicBuilder.name(DELIVERIES_TOPIC).partitions(12).replicas(1).build();
	}

	/**
	 * A listener that fails (Postgres or Redis is down, say) retries the same message once a second
	 * until it works. Skipping the message instead would silently drop the work.
	 */
	@Bean
	CommonErrorHandler kafkaErrorHandler() {
		return new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
	}

}
