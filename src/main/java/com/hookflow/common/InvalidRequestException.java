package com.hookflow.common;

/** The request is well-formed JSON but breaks one of Hookflow's rules. */
public class InvalidRequestException extends RuntimeException {

	public InvalidRequestException(String message) {
		super(message);
	}

}
