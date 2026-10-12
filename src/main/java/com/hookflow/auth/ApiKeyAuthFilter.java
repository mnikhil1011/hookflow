package com.hookflow.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import com.hookflow.config.HookflowProperties;
import com.hookflow.tenant.ApiKeyGenerator;
import com.hookflow.tenant.Tenant;
import com.hookflow.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authenticates every {@code /v1/**} request.
 * <ul>
 * <li>Creating a tenant is an admin action and needs the {@code X-Admin-Token} header.</li>
 * <li>Everything else needs {@code Authorization: Bearer <api key>}. The matching tenant's ID is
 * stored as a request attribute, which controllers read with {@code @RequestAttribute(TENANT_ID)}.</li>
 * </ul>
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String TENANT_ID = "hookflow.tenantId";

	private static final String ADMIN_PATH = "/v1/tenants";
	private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
	private static final String BEARER_PREFIX = "Bearer ";

	private final TenantRepository tenants;
	private final byte[] adminToken;
	private final JsonMapper json;

	public ApiKeyAuthFilter(TenantRepository tenants, HookflowProperties properties, JsonMapper json) {
		this.tenants = tenants;
		this.adminToken = properties.adminToken().getBytes(StandardCharsets.UTF_8);
		this.json = json;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/v1/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getRequestURI().equals(ADMIN_PATH)) {
			if (!hasValidAdminToken(request)) {
				reject(response, "A valid " + ADMIN_TOKEN_HEADER + " header is required");
				return;
			}
		}
		else {
			Optional<Tenant> tenant;
			try {
				tenant = findTenant(request);
			}
			catch (DataAccessException | TransactionException e) {
				// The key cannot be checked while the database is unreachable, so say "try again later".
				respond(response, HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable");
				return;
			}
			if (tenant.isEmpty()) {
				reject(response, "A valid API key is required: Authorization: Bearer <api key>");
				return;
			}
			request.setAttribute(TENANT_ID, tenant.get().getId());
		}
		chain.doFilter(request, response);
	}

	private boolean hasValidAdminToken(HttpServletRequest request) {
		String token = request.getHeader(ADMIN_TOKEN_HEADER);
		// MessageDigest.isEqual compares in constant time, so response timing reveals nothing about the token.
		return token != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), adminToken);
	}

	private Optional<Tenant> findTenant(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return Optional.empty();
		}
		String apiKey = header.substring(BEARER_PREFIX.length()).trim();
		return tenants.findByApiKeyHash(ApiKeyGenerator.hash(apiKey));
	}

	private void reject(HttpServletResponse response, String detail) throws IOException {
		respond(response, HttpStatus.UNAUTHORIZED, detail);
	}

	private void respond(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(json.writeValueAsString(problem));
	}

}
