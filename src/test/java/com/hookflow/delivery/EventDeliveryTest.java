package com.hookflow.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hookflow.ApiClient.TestEndpoint;
import com.hookflow.ApiClient.TestTenant;
import com.hookflow.IntegrationTest;
import com.hookflow.TestReceiver;
import com.hookflow.TestReceiver.ReceivedWebhook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Follows events all the way through: API, Postgres, Kafka, the worker and out to a receiver.
 * The test profile allows three attempts per delivery, 200 ms apart.
 */
class EventDeliveryTest extends IntegrationTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final Map<String, Object> PAYLOAD = Map.of("orderId", 1042, "amount", 4999);

	private static TestReceiver receiver;

	private TestTenant tenant;
	private TestEndpoint endpoint;
	/** A path no other test uses, so each test sees only its own webhooks. */
	private String path;

	@BeforeAll
	static void startReceiver() {
		receiver = new TestReceiver();
	}

	@AfterAll
	static void stopReceiver() {
		receiver.close();
	}

	@BeforeEach
	void createTenantWithOneEndpoint() {
		path = "/hook/" + UUID.randomUUID();
		tenant = api.createTenant();
		endpoint = api.createEndpoint(tenant, receiver.url(path));
	}

	@Test
	void deliversAnEventAsASignedWebhook() {
		String eventId = api.sendEvent(tenant, "order.paid", PAYLOAD);

		awaitDeliveryStatus(eventId, "SUCCEEDED");

		List<ReceivedWebhook> received = receiver.receivedAt(path);
		assertThat(received).hasSize(1);
		ReceivedWebhook webhook = received.getFirst();
		assertThat(webhook.id()).isEqualTo(eventId);
		assertThat(webhook.body()).contains("\"type\":\"order.paid\"").contains("\"orderId\":1042");
		// The receiver can verify the signature with the secret it was given at registration.
		String expectedSignature = new WebhookSigner().sign(endpoint.signingSecret(), webhook.id(),
				webhook.timestamp(), webhook.body());
		assertThat(webhook.signature()).isEqualTo(expectedSignature);
	}

	@Test
	void recordsEveryAttemptOfADelivery() {
		String eventId = api.sendEvent(tenant, "order.paid", PAYLOAD);
		String deliveryId = awaitDeliveryStatus(eventId, "SUCCEEDED").get("id").asString();

		JsonNode detail = api.get("/v1/deliveries/" + deliveryId, tenant.apiKey()).getBody();

		assertThat(detail.get("delivery").get("attemptCount").asInt()).isEqualTo(1);
		assertThat(detail.get("attempts")).hasSize(1);
		assertThat(detail.get("attempts").get(0).get("responseStatus").asInt()).isEqualTo(200);
	}

	@Test
	void theSameIdempotencyKeyCreatesOnlyOneEvent() {
		ResponseEntity<JsonNode> first = api.sendEvent(tenant, "order.paid", PAYLOAD, "order-1042-paid");
		ResponseEntity<JsonNode> second = api.sendEvent(tenant, "order.paid", PAYLOAD, "order-1042-paid");

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		String eventId = first.getBody().get("id").asString();
		assertThat(second.getBody().get("id").asString()).isEqualTo(eventId);

		awaitDeliveryStatus(eventId, "SUCCEEDED");
		assertThat(receiver.receivedAt(path)).hasSize(1);
	}

	@Test
	void retriesAfterAServerErrorUntilTheReceiverRecovers() {
		receiver.respondWith(path, 500, 503);

		String eventId = api.sendEvent(tenant, "order.paid", PAYLOAD);

		JsonNode delivery = awaitDeliveryStatus(eventId, "SUCCEEDED");
		assertThat(delivery.get("attemptCount").asInt()).isEqualTo(3);
		// Every retry carries the same webhook ID, which is how receivers spot duplicates.
		assertThat(receiver.receivedAt(path)).hasSize(3).allMatch(webhook -> webhook.id().equals(eventId));
	}

	@Test
	void givesUpAfterTheLastAttemptAndCanBeReplayed() {
		receiver.alwaysRespondWith(path, 500);

		String eventId = api.sendEvent(tenant, "order.paid", PAYLOAD);

		JsonNode dead = awaitDeliveryStatus(eventId, "DEAD");
		assertThat(dead.get("attemptCount").asInt()).isEqualTo(3);
		assertThat(dead.get("nextAttemptAt").isNull()).isTrue();

		// The receiver is fixed, and the dead delivery is replayed by hand.
		receiver.alwaysRespondWith(path, 200);
		ResponseEntity<JsonNode> replay = api.post("/v1/deliveries/" + dead.get("id").asString() + "/replay",
				tenant.apiKey(), null);

		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		awaitDeliveryStatus(eventId, "SUCCEEDED");
	}

	@Test
	void doesNotRetryAResponseThatRetryingCannotFix() {
		receiver.alwaysRespondWith(path, 400);

		String eventId = api.sendEvent(tenant, "order.paid", PAYLOAD);

		JsonNode dead = awaitDeliveryStatus(eventId, "DEAD");
		assertThat(dead.get("attemptCount").asInt()).isEqualTo(1);
	}

	@Test
	void onlyADeadDeliveryCanBeReplayed() {
		String eventId = api.sendEvent(tenant, "order.paid", PAYLOAD);
		String deliveryId = awaitDeliveryStatus(eventId, "SUCCEEDED").get("id").asString();

		ResponseEntity<JsonNode> replay = api.post("/v1/deliveries/" + deliveryId + "/replay", tenant.apiKey(), null);

		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void sendsNothingToADisabledEndpoint() {
		api.patch("/v1/endpoints/" + endpoint.id(), tenant.apiKey(), Map.of("enabled", false));
		// A second, enabled endpoint shows when fan-out has finished.
		String otherPath = "/hook/" + UUID.randomUUID();
		api.createEndpoint(tenant, receiver.url(otherPath));

		String eventId = api.sendEvent(tenant, "order.paid", PAYLOAD);

		awaitDeliveryStatus(eventId, "SUCCEEDED");
		assertThat(receiver.receivedAt(otherPath)).hasSize(1);
		assertThat(receiver.receivedAt(path)).isEmpty();
		assertThat(deliveriesOf(eventId)).hasSize(1);
	}

	@Test
	void listsDeliveriesFilteredByStatus() {
		String eventId = api.sendEvent(tenant, "order.paid", PAYLOAD);
		awaitDeliveryStatus(eventId, "SUCCEEDED");

		JsonNode succeeded = api.get("/v1/deliveries?status=SUCCEEDED&endpointId=" + endpoint.id(), tenant.apiKey()).getBody();
		JsonNode dead = api.get("/v1/deliveries?status=DEAD", tenant.apiKey()).getBody();
		JsonNode all = api.get("/v1/deliveries", tenant.apiKey()).getBody();

		assertThat(succeeded.get("items")).hasSize(1);
		assertThat(succeeded.get("totalItems").asInt()).isEqualTo(1);
		assertThat(dead.get("items")).isEmpty();
		assertThat(all.get("items")).hasSize(1);
	}

	@Test
	void rejectsAnEventWithoutATypeOrPayload() {
		ResponseEntity<JsonNode> response = api.post("/v1/events", tenant.apiKey(), Map.of("type", ""));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().get("errors").has("type")).isTrue();
		assertThat(response.getBody().get("errors").has("payload")).isTrue();
	}

	@Test
	void oneTenantCannotReadAnotherTenantsEvent() {
		String eventId = api.sendEvent(tenant, "order.paid", PAYLOAD);
		TestTenant stranger = api.createTenant();

		assertThat(api.get("/v1/events/" + eventId, stranger.apiKey()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	/** Waits until the event's single delivery reaches the status, then returns that delivery. */
	private JsonNode awaitDeliveryStatus(String eventId, String status) {
		await().atMost(TIMEOUT).untilAsserted(() -> {
			JsonNode deliveries = deliveriesOf(eventId);
			assertThat(deliveries).hasSize(1);
			assertThat(deliveries.get(0).get("status").asString()).isEqualTo(status);
		});
		return deliveriesOf(eventId).get(0);
	}

	private JsonNode deliveriesOf(String eventId) {
		return api.get("/v1/events/" + eventId, tenant.apiKey()).getBody().get("deliveries");
	}

}
