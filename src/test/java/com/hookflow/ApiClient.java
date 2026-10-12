package com.hookflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Calls the running application's REST API the way a real client would. */
public class ApiClient {

	public static final String ADMIN_TOKEN = "test-admin-token";

	/** A tenant created for a test, with the API key needed to act as it. */
	public record TestTenant(String id, String apiKey) {
	}

	/** An endpoint created for a test, with the secret needed to verify its webhooks. */
	public record TestEndpoint(String id, String signingSecret) {
	}

	private final RestClient http;

	public ApiClient(int port) {
		this.http = RestClient.builder()
				.baseUrl("http://localhost:" + port)
				// Never throw on 4xx or 5xx: tests assert on the status themselves.
				.defaultStatusHandler(status -> true, (request, response) -> { })
				.build();
	}

	/** Sends a request. Pass a null API key or body to leave them out. */
	public ResponseEntity<JsonNode> call(HttpMethod method, String path, String apiKey, Object body,
			Map<String, String> extraHeaders) {
		RestClient.RequestBodySpec request = http.method(method).uri(path);
		if (apiKey != null) {
			request.header("Authorization", "Bearer " + apiKey);
		}
		extraHeaders.forEach(request::header);
		if (body != null) {
			request.body(body);
		}
		return request.retrieve().toEntity(JsonNode.class);
	}

	public ResponseEntity<JsonNode> get(String path, String apiKey) {
		return call(HttpMethod.GET, path, apiKey, null, Map.of());
	}

	public ResponseEntity<JsonNode> post(String path, String apiKey, Object body) {
		return call(HttpMethod.POST, path, apiKey, body, Map.of());
	}

	public ResponseEntity<JsonNode> patch(String path, String apiKey, Object body) {
		return call(HttpMethod.PATCH, path, apiKey, body, Map.of());
	}

	public TestTenant createTenant() {
		ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/v1/tenants", null, Map.of("name", "Test tenant"),
				Map.of("X-Admin-Token", ADMIN_TOKEN));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return new TestTenant(response.getBody().get("id").asString(), response.getBody().get("apiKey").asString());
	}

	public TestEndpoint createEndpoint(TestTenant tenant, String url) {
		ResponseEntity<JsonNode> response = post("/v1/endpoints", tenant.apiKey(), Map.of("url", url));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return new TestEndpoint(response.getBody().get("id").asString(),
				response.getBody().get("signingSecret").asString());
	}

	/** Sends an event and returns the raw response. Pass a null idempotency key to leave it out. */
	public ResponseEntity<JsonNode> sendEvent(TestTenant tenant, String type, Map<String, Object> payload,
			String idempotencyKey) {
		Map<String, String> headers = idempotencyKey == null ? Map.of() : Map.of("Idempotency-Key", idempotencyKey);
		return call(HttpMethod.POST, "/v1/events", tenant.apiKey(), Map.of("type", type, "payload", payload), headers);
	}

	/** Sends an event that is expected to be accepted, and returns its ID. */
	public String sendEvent(TestTenant tenant, String type, Map<String, Object> payload) {
		ResponseEntity<JsonNode> response = sendEvent(tenant, type, payload, null);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		return response.getBody().get("id").asString();
	}

}
