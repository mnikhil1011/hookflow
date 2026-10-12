package com.hookflow.flowcontrol;

import java.util.List;
import java.util.UUID;

/**
 * Applies no per-endpoint limit at all. It exists as the baseline for benchmarks: it shows what a
 * slow endpoint does to everyone else when nothing isolates it.
 */
public class NoFlowController implements FlowController {

	@Override
	public SlotDecision acquire(UUID endpointId, UUID deliveryId) {
		return SlotDecision.GRANTED;
	}

	@Override
	public List<UUID> release(UUID endpointId, UUID deliveryId, Signal signal) {
		return List.of();
	}

	@Override
	public FlowState state(UUID endpointId) {
		return new FlowState(null, 0, 0, false);
	}

}
