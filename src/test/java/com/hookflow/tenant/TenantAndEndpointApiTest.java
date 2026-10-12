package com.hookflow.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.hookflow.ApiClient;
import com.hookflow.ApiClient.TestEndpoint;
import com.hookflow.ApiClient.TestTenant;
import com.hookflow.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class TenantAndEndpointApiTest extends IntegrationTest {

	private static final String ENDPOINT_URL = "http://localhost:9000/hook";

	@Test
	void creatingATenantNeedsTheAdminToken() {
		Map<String, String> body = Map.of("name", "Acme");

		ResponseEntity<JsonNode> withoutToken = api.call(HttpMethod.POST, "/v1/tenants", null, body, Map.of());
		ResponseEntity<JsonNode> withWrongToken = api.call(HttpMethod.POST, "/v1/tenants", null, body,
				Map.of("X-Admin-Token", "wrong"));
		ResponseEntity<JsonNode> withToken = api.call(HttpMethod.POST, "/v1/tenants", null, body,
				Map.of("X-Admin-Token", ApiClient.ADMIN_TOKEN));

		assertThat(withoutToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(withWrongToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(withToken.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(withToken.getBody().get("apiKey").asString()).startsWith("hf_");
	}

	@Test
	void rejectsRequestsWithoutAValidApiKey() {
		assertThat(api.get("/v1/endpoints", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(api.get("/v1/endpoints", "hf_not-a-real-key").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void aTenantCanRegisterListAndUpdateItsEndpoints() {
		TestTenant tenant = api.createTenant();

		ResponseEntity<JsonNode> created = api.post("/v1/endpoints", tenant.apiKey(), Map.of("url", ENDPOINT_URL));
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody().get("signingSecret").asString()).startsWith("whsec_");
		String endpointId = created.getBody().get("id").asString();

		JsonNode listed = api.get("/v1/endpoints", tenant.apiKey()).getBody();
		assertThat(listed).hasSize(1);
		assertThat(listed.get(0).get("url").asString()).isEqualTo(ENDPOINT_URL);
		// The secret is shown once, at creation, and never again.
		assertThat(listed.get(0).has("signingSecret")).isFalse();

		ResponseEntity<JsonNode> updated = api.patch("/v1/endpoints/" + endpointId, tenant.apiKey(),
				Map.of("enabled", false));
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody().get("enabled").asBoolean()).isFalse();
		assertThat(updated.getBody().get("url").asString()).isEqualTo(ENDPOINT_URL);
	}

	@Test
	void oneTenantCannotSeeOrChangeAnotherTenantsEndpoint() {
		TestTenant owner = api.createTenant();
		TestTenant stranger = api.createTenant();
		TestEndpoint endpoint = api.createEndpoint(owner, ENDPOINT_URL);
		String path = "/v1/endpoints/" + endpoint.id();

		// 404 rather than 403, so the API does not even confirm that the ID exists.
		assertThat(api.patch(path, stranger.apiKey(), Map.of("enabled", false)).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(api.get(path + "/flow", stranger.apiKey()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(api.get("/v1/endpoints", stranger.apiKey()).getBody()).isEmpty();
	}

	@Test
	void rejectsAnInvalidEndpointUrl() {
		TestTenant tenant = api.createTenant();

		ResponseEntity<JsonNode> blank = api.post("/v1/endpoints", tenant.apiKey(), Map.of("url", ""));
		ResponseEntity<JsonNode> notHttp = api.post("/v1/endpoints", tenant.apiKey(), Map.of("url", "ftp://example.com"));

		assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(blank.getBody().get("errors").has("url")).isTrue();
		assertThat(notHttp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void showsTheFlowControlStateOfAnEndpoint() {
		TestTenant tenant = api.createTenant();
		TestEndpoint endpoint = api.createEndpoint(tenant, ENDPOINT_URL);

		JsonNode flow = api.get("/v1/endpoints/" + endpoint.id() + "/flow", tenant.apiKey()).getBody();

		// A new endpoint starts at the configured initial limit with nothing in flight.
		assertThat(flow.get("limit").asDouble()).isEqualTo(4.0);
		assertThat(flow.get("inFlight").asInt()).isZero();
		assertThat(flow.get("waiting").asInt()).isZero();
		assertThat(flow.get("paused").asBoolean()).isFalse();
	}

}
