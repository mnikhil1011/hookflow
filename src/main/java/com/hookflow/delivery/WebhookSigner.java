package com.hookflow.delivery;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.hookflow.endpoint.SigningSecretGenerator;
import org.springframework.stereotype.Component;

/**
 * Signs webhooks the way the Standard Webhooks specification describes, so receivers can verify
 * them with existing libraries: HMAC-SHA256 over {@code id.timestamp.body}, base64 encoded, with a
 * {@code v1,} version prefix.
 */
@Component
public class WebhookSigner {

	private static final String ALGORITHM = "HmacSHA256";

	/**
	 * @param secret    the endpoint's signing secret, in the form {@code whsec_<base64>}
	 * @param timestamp seconds since the epoch, exactly as sent in the {@code webhook-timestamp} header
	 * @param body      the exact request body that will be sent
	 */
	public String sign(String secret, String webhookId, String timestamp, String body) {
		byte[] key = Base64.getDecoder().decode(secret.substring(SigningSecretGenerator.PREFIX.length()));
		String signedContent = webhookId + "." + timestamp + "." + body;
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			byte[] signature = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
			return "v1," + Base64.getEncoder().encodeToString(signature);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Could not sign the webhook", e);
		}
	}

}
