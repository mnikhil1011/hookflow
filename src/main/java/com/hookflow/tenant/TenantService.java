package com.hookflow.tenant;

import org.springframework.stereotype.Service;

@Service
public class TenantService {

	/** A new tenant together with its API key, which is never retrievable again. */
	public record CreatedTenant(Tenant tenant, String apiKey) {
	}

	private final TenantRepository tenants;
	private final ApiKeyGenerator apiKeys;

	public TenantService(TenantRepository tenants, ApiKeyGenerator apiKeys) {
		this.tenants = tenants;
		this.apiKeys = apiKeys;
	}

	public CreatedTenant create(String name) {
		String apiKey = apiKeys.generate();
		Tenant tenant = tenants.save(new Tenant(name, ApiKeyGenerator.hash(apiKey)));
		return new CreatedTenant(tenant, apiKey);
	}

}
