package com.hookflow.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignerTest {

	private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
	private static final String WEBHOOK_ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
	private static final String TIMESTAMP = "1614265330";
	private static final String BODY = "{\"test\": 2432232314}";

	private final WebhookSigner signer = new WebhookSigner();

	/** The inputs and expected signature are the published Standard Webhooks example. */
	@Test
	void matchesThePublishedStandardWebhooksExample() {
		String signature = signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

		assertThat(signature).isEqualTo("v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=");
	}

	@Test
	void changesWhenAnyPartOfTheMessageChanges() {
		String original = signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

		assertThat(signer.sign(SECRET, "msg_other", TIMESTAMP, BODY)).isNotEqualTo(original);
		assertThat(signer.sign(SECRET, WEBHOOK_ID, "1614265331", BODY)).isNotEqualTo(original);
		assertThat(signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, "{\"test\": 1}")).isNotEqualTo(original);
	}

	@Test
	void changesWhenTheSecretChanges() {
		String original = signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

		assertThat(signer.sign("whsec_c2VjcmV0", WEBHOOK_ID, TIMESTAMP, BODY)).isNotEqualTo(original);
	}

}
