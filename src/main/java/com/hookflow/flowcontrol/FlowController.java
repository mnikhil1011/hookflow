package com.hookflow.flowcontrol;

import java.util.List;
import java.util.UUID;

/**
 * Decides how many requests may be in flight to each endpoint at the same time.
 * <p>
 * A worker must {@link #acquire} a slot before sending and {@link #release} it afterwards, reporting
 * how the attempt went. Implementations differ only in how they set each endpoint's limit.
 */
public interface FlowController {

	/** Tries to take a slot on the endpoint for this delivery. */
	SlotDecision acquire(UUID endpointId, UUID deliveryId);

	/**
	 * Frees the delivery's slot and reports the outcome of its attempt.
	 *
	 * @return deliveries that were waiting for this endpoint and now hold a slot; the caller must send them
	 */
	List<UUID> release(UUID endpointId, UUID deliveryId, Signal signal);

	/** A snapshot of the endpoint's current state, for the API. */
	FlowState state(UUID endpointId);

	enum SlotDecision {
		/** The delivery holds a slot and may be sent now. */
		GRANTED,
		/** No slot is free. The delivery waits and is handed a slot later. */
		PARKED,
		/** This delivery already holds a slot, so another worker is sending it. */
		DUPLICATE
	}

	/** What an attempt says about the endpoint's health. This is separate from whether the delivery succeeded. */
	enum Signal {
		/** The endpoint answered promptly. A "400 Bad Request" counts: the server is healthy. */
		OK,
		/** The endpoint timed out, refused the connection or reported overload. */
		CONGESTED,
		/** No request was sent, so nothing was learned. */
		NONE
	}

}
