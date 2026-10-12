package com.hookflow.endpoint;

import java.util.List;
import java.util.UUID;

import com.hookflow.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {

	private final EndpointRepository endpoints;
	private final UrlValidator urlValidator;
	private final SigningSecretGenerator secrets;

	public EndpointService(EndpointRepository endpoints, UrlValidator urlValidator, SigningSecretGenerator secrets) {
		this.endpoints = endpoints;
		this.urlValidator = urlValidator;
		this.secrets = secrets;
	}

	public Endpoint create(UUID tenantId, String url) {
		urlValidator.validate(url);
		return endpoints.save(new Endpoint(tenantId, url, secrets.generate()));
	}

	public List<Endpoint> list(UUID tenantId) {
		return endpoints.findByTenantIdOrderByCreatedAt(tenantId);
	}

	/** Looks up an endpoint, treating another tenant's endpoint as if it did not exist. */
	public Endpoint get(UUID tenantId, UUID endpointId) {
		return endpoints.findByIdAndTenantId(endpointId, tenantId)
				.orElseThrow(() -> new NotFoundException("Endpoint not found"));
	}

	/** Applies the fields that are present; a null argument leaves that field unchanged. */
	@Transactional
	public Endpoint update(UUID tenantId, UUID endpointId, String url, Boolean enabled) {
		Endpoint endpoint = get(tenantId, endpointId);
		if (url != null) {
			urlValidator.validate(url);
			endpoint.changeUrl(url);
		}
		if (enabled != null) {
			endpoint.setEnabled(enabled);
		}
		return endpoint;
	}

}
