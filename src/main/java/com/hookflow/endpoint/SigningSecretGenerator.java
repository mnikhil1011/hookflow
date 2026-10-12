package com.hookflow.endpoint;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Creates the secret used to sign every request to an endpoint.
 * The format, base64 with a {@code whsec_} prefix, comes from the Standard Webhooks specification.
 */
@Component
public class SigningSecretGenerator {

	public static final String PREFIX = "whsec_";
	private static final int SECRET_BYTES = 24;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[SECRET_BYTES];
		random.nextBytes(bytes);
		return PREFIX + Base64.getEncoder().encodeToString(bytes);
	}

}
