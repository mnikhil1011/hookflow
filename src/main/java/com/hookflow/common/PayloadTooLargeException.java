package com.hookflow.common;

/** The request body is larger than Hookflow accepts. */
public class PayloadTooLargeException extends RuntimeException {

	public PayloadTooLargeException(String message) {
		super(message);
	}

}
