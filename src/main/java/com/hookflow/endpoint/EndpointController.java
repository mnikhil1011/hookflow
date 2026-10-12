package com.hookflow.endpoint;

import static com.hookflow.auth.ApiKeyAuthFilter.TENANT_ID;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hookflow.flowcontrol.FlowController;
import com.hookflow.flowcontrol.FlowState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/endpoints")
public class EndpointController {

	record CreateEndpointRequest(@NotBlank @Size(max = 2000) String url) {
	}

	record UpdateEndpointRequest(@Size(max = 2000) String url, Boolean enabled) {
	}

	record EndpointResponse(UUID id, String url, boolean enabled, Instant createdAt) {

		static EndpointResponse of(Endpoint endpoint) {
			return new EndpointResponse(endpoint.getId(), endpoint.getUrl(), endpoint.isEnabled(),
					endpoint.getCreatedAt());
		}
	}

	/** Returned only when an endpoint is created; the secret is not shown again. */
	record EndpointCreatedResponse(UUID id, String url, boolean enabled, String signingSecret, Instant createdAt) {
	}

	private final EndpointService endpointService;
	private final FlowController flowController;

	public EndpointController(EndpointService endpointService, FlowController flowController) {
		this.endpointService = endpointService;
		this.flowController = flowController;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	EndpointCreatedResponse create(@RequestAttribute(TENANT_ID) UUID tenantId,
			@Valid @RequestBody CreateEndpointRequest request) {
		Endpoint endpoint = endpointService.create(tenantId, request.url());
		return new EndpointCreatedResponse(endpoint.getId(), endpoint.getUrl(), endpoint.isEnabled(),
				endpoint.getSigningSecret(), endpoint.getCreatedAt());
	}

	@GetMapping
	List<EndpointResponse> list(@RequestAttribute(TENANT_ID) UUID tenantId) {
		return endpointService.list(tenantId).stream().map(EndpointResponse::of).toList();
	}

	@PatchMapping("/{id}")
	EndpointResponse update(@RequestAttribute(TENANT_ID) UUID tenantId, @PathVariable UUID id,
			@Valid @RequestBody UpdateEndpointRequest request) {
		return EndpointResponse.of(endpointService.update(tenantId, id, request.url(), request.enabled()));
	}

	/** Shows the endpoint's live flow-control state: its limit, requests in flight and deliveries waiting. */
	@GetMapping("/{id}/flow")
	FlowState flow(@RequestAttribute(TENANT_ID) UUID tenantId, @PathVariable UUID id) {
		Endpoint endpoint = endpointService.get(tenantId, id);
		return flowController.state(endpoint.getId());
	}

}
