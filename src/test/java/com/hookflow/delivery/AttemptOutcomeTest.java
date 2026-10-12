package com.hookflow.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.hookflow.flowcontrol.FlowController.Signal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AttemptOutcomeTest {

	@ParameterizedTest(name = "status {0} is {1}")
	@CsvSource({
			"200, SUCCESS",
			"201, SUCCESS",
			"204, SUCCESS",
			"301, PERMANENT_FAILURE",
			"400, PERMANENT_FAILURE",
			"401, PERMANENT_FAILURE",
			"404, PERMANENT_FAILURE",
			"408, RETRYABLE_FAILURE",
			"429, RETRYABLE_FAILURE",
			"500, RETRYABLE_FAILURE",
			"503, RETRYABLE_FAILURE" })
	void classifiesResponsesByStatus(int status, AttemptOutcome expected) {
		SendResult result = SendResult.responded(Instant.now(), Duration.ofMillis(20), status, "");

		assertThat(AttemptOutcome.of(result)).isEqualTo(expected);
	}

	@Test
	void treatsNoResponseAsRetryable() {
		SendResult timeout = SendResult.failed(Instant.now(), Duration.ofSeconds(10), "TIMEOUT");

		assertThat(AttemptOutcome.of(timeout)).isEqualTo(AttemptOutcome.RETRYABLE_FAILURE);
	}

	@Test
	void onlyRetryableFailuresSignalCongestion() {
		assertThat(AttemptOutcome.SUCCESS.flowSignal()).isEqualTo(Signal.OK);
		assertThat(AttemptOutcome.RETRYABLE_FAILURE.flowSignal()).isEqualTo(Signal.CONGESTED);
		// A 400 kills the delivery, but the server answered promptly, so the endpoint is healthy.
		assertThat(AttemptOutcome.PERMANENT_FAILURE.flowSignal()).isEqualTo(Signal.OK);
	}

}
