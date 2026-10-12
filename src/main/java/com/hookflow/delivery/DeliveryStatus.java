package com.hookflow.delivery;

public enum DeliveryStatus {

	/** Created and not attempted yet (or replayed). */
	PENDING,

	/** Failed at least once and waiting for its next attempt. */
	RETRYING,

	/** The endpoint answered with a 2xx status. Final. */
	SUCCEEDED,

	/** Gave up: out of attempts, or the endpoint gave an answer that retrying cannot fix. Can be replayed. */
	DEAD

}
