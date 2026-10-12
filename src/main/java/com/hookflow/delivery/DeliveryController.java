package com.hookflow.delivery;

import static com.hookflow.auth.ApiKeyAuthFilter.TENANT_ID;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hookflow.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/deliveries")
public class DeliveryController {

	private static final int MAX_PAGE_SIZE = 200;

	record AttemptResponse(int attemptNumber, Instant startedAt, int durationMs, Integer responseStatus, String error,
			String responseSnippet) {

		static AttemptResponse of(DeliveryAttempt attempt) {
			return new AttemptResponse(attempt.getAttemptNumber(), attempt.getStartedAt(), attempt.getDurationMs(),
					attempt.getResponseStatus(), attempt.getError(), attempt.getResponseSnippet());
		}
	}

	record DeliveryDetailResponse(DeliveryResponse delivery, List<AttemptResponse> attempts) {
	}

	private final DeliveryService deliveryService;

	public DeliveryController(DeliveryService deliveryService) {
		this.deliveryService = deliveryService;
	}

	@GetMapping
	PageResponse<DeliveryResponse> list(@RequestAttribute(TENANT_ID) UUID tenantId,
			@RequestParam(required = false) UUID endpointId,
			@RequestParam(required = false) DeliveryStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		return PageResponse.of(deliveryService.search(tenantId, endpointId, status, safePage, safeSize)
				.map(DeliveryResponse::of));
	}

	@GetMapping("/{id}")
	DeliveryDetailResponse get(@RequestAttribute(TENANT_ID) UUID tenantId, @PathVariable UUID id) {
		Delivery delivery = deliveryService.get(tenantId, id);
		List<AttemptResponse> attempts = deliveryService.attemptsOf(id).stream().map(AttemptResponse::of).toList();
		return new DeliveryDetailResponse(DeliveryResponse.of(delivery), attempts);
	}

	@PostMapping("/{id}/replay")
	@ResponseStatus(HttpStatus.ACCEPTED)
	DeliveryResponse replay(@RequestAttribute(TENANT_ID) UUID tenantId, @PathVariable UUID id) {
		return DeliveryResponse.of(deliveryService.replay(tenantId, id));
	}

}
