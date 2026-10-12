package com.hookflow.common;

/** The request cannot be applied to the resource in its current state. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
