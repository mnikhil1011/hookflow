package com.hookflow.flowcontrol;

/**
 * @param limit    most requests allowed in flight at once; null when there is no limit
 * @param inFlight requests in flight right now
 * @param waiting  deliveries waiting for a slot
 * @param paused   true while the endpoint is paused after failing at its minimum limit
 */
public record FlowState(Double limit, long inFlight, long waiting, boolean paused) {
}
