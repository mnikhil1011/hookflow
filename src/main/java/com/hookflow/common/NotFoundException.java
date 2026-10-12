package com.hookflow.common;

/** The requested resource does not exist, or belongs to another tenant. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
