package com.hookflow.delivery;

import java.util.UUID;

import com.hookflow.config.KafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Takes due deliveries from Kafka and passes them to the dispatcher. */
@Component
public class DeliveryConsumer {

	private static final Logger log = LoggerFactory.getLogger(DeliveryConsumer.class);

	private final DeliveryDispatcher dispatcher;

	public DeliveryConsumer(DeliveryDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	/** The message key is the endpoint ID and the value is the delivery ID. */
	@KafkaListener(topics = KafkaConfig.DELIVERIES_TOPIC, groupId = "hookflow-delivery", concurrency = "3")
	public void onDelivery(ConsumerRecord<String, String> message) throws InterruptedException {
		UUID endpointId;
		UUID deliveryId;
		try {
			endpointId = UUID.fromString(message.key());
			deliveryId = UUID.fromString(message.value());
		}
		catch (IllegalArgumentException | NullPointerException e) {
			log.warn("Ignoring a malformed delivery message: key={} value={}", message.key(), message.value());
			return;
		}
		dispatcher.dispatch(endpointId, deliveryId);
	}

}
