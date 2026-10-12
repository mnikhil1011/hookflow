package com.hookflow.delivery;

import com.hookflow.flowcontrol.FlowController.Signal;

/** What one attempt means for the delivery. */
public enum AttemptOutcome {

	/** Any 2xx response. */
	SUCCESS,

	/** A problem that may go away: no response, a 5xx, 408 Request Timeout or 429 Too Many Requests. */
	RETRYABLE_FAILURE,

	/** Any other response, such as 400 or 404. Sending the same request again will not change the answer. */
	PERMANENT_FAILURE;

	public static AttemptOutcome of(SendResult result) {
		if (result.status() == null) {
			return RETRYABLE_FAILURE;
		}
		int status = result.status();
		if (status >= 200 && status < 300) {
			return SUCCESS;
		}
		if (status >= 500 || status == 408 || status == 429) {
			return RETRYABLE_FAILURE;
		}
		return PERMANENT_FAILURE;
	}

	/**
	 * What the attempt says about the endpoint's health. A permanent failure still counts as OK:
	 * the server answered promptly, it just did not like the request.
	 */
	public Signal flowSignal() {
		return this == RETRYABLE_FAILURE ? Signal.CONGESTED : Signal.OK;
	}

}
