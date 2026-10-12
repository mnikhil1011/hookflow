package com.hookflow.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Creates API keys and the hashes stored for them.
 * <p>
 * Only the SHA-256 hash is saved, so a database leak does not expose usable keys. A fast hash is
 * enough here because the key is 32 random bytes; slow password hashes exist to protect short,
 * guessable passwords.
 */
@Component
public class ApiKeyGenerator {

	private static final String PREFIX = "hf_";
	private static final int KEY_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[KEY_BYTES];
		random.nextBytes(bytes);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String apiKey) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha256.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
		}
	}

}
