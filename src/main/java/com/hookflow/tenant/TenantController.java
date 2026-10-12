package com.hookflow.tenant;

import java.time.Instant;
import java.util.UUID;

import com.hookflow.tenant.TenantService.CreatedTenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin API. Requests are checked for the admin token by {@code ApiKeyAuthFilter}. */
@RestController
@RequestMapping("/v1/tenants")
public class TenantController {

	record CreateTenantRequest(@NotBlank @Size(max = 200) String name) {
	}

	record TenantCreatedResponse(UUID id, String name, String apiKey, Instant createdAt) {
	}

	private final TenantService tenantService;

	public TenantController(TenantService tenantService) {
		this.tenantService = tenantService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	TenantCreatedResponse create(@Valid @RequestBody CreateTenantRequest request) {
		CreatedTenant created = tenantService.create(request.name());
		Tenant tenant = created.tenant();
		return new TenantCreatedResponse(tenant.getId(), tenant.getName(), created.apiKey(), tenant.getCreatedAt());
	}

}
