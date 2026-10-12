package com.hookflow.event;

import static com.hookflow.auth.ApiKeyAuthFilter.TENANT_ID;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hookflow.delivery.DeliveryRepository;
import com.hookflow.delivery.DeliveryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/v1/events")
public class EventController {

	record SendEventRequest(@NotBlank @Size(max = 100) String type, @NotNull JsonNode payload) {
	}

	record EventAcceptedResponse(UUID id, String status) {
	}

	record EventResponse(UUID id, String type, JsonNode payload, Instant createdAt, List<DeliveryResponse> deliveries) {
	}

	private final EventService eventService;
	private final DeliveryRepository deliveries;
	private final JsonMapper json;

	public EventController(EventService eventService, DeliveryRepository deliveries, JsonMapper json) {
		this.eventService = eventService;
		this.deliveries = deliveries;
		this.json = json;
	}

	/** 202 means "stored and will be delivered", not "delivered". */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	EventAcceptedResponse send(@RequestAttribute(TENANT_ID) UUID tenantId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody SendEventRequest request) {
		String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey;
		Event event = eventService.accept(tenantId, request.type(), request.payload().toString(), key);
		return new EventAcceptedResponse(event.getId(), "accepted");
	}

	@GetMapping("/{id}")
	EventResponse get(@RequestAttribute(TENANT_ID) UUID tenantId, @PathVariable UUID id) {
		Event event = eventService.get(tenantId, id);
		List<DeliveryResponse> eventDeliveries = deliveries.findByEventIdOrderByCreatedAt(id).stream()
				.map(DeliveryResponse::of)
				.toList();
		return new EventResponse(event.getId(), event.getEventType(), json.readTree(event.getPayload()),
				event.getCreatedAt(), eventDeliveries);
	}

}
