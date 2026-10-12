package com.hookflow.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import com.hookflow.TestProperties;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

	private static final List<Duration> DELAYS = List.of(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2));

	@Test
	void allowsOneAttemptPlusOneRetryPerDelay() {
		RetryPolicy policy = new RetryPolicy(TestProperties.withRetries(DELAYS, 0));

		assertThat(policy.maxAttempts()).isEqualTo(4);
		assertThat(policy.hasAttemptsLeft(3)).isTrue();
		assertThat(policy.hasAttemptsLeft(4)).isFalse();
	}

	@Test
	void usesTheConfiguredDelayBeforeEachRetry() {
		RetryPolicy policy = new RetryPolicy(TestProperties.withRetries(DELAYS, 0));

		assertThat(policy.delayBefore(2)).isEqualTo(Duration.ofSeconds(5));
		assertThat(policy.delayBefore(3)).isEqualTo(Duration.ofSeconds(30));
		assertThat(policy.delayBefore(4)).isEqualTo(Duration.ofMinutes(2));
	}

	@Test
	void keepsJitterWithinTheConfiguredRange() {
		RetryPolicy policy = new RetryPolicy(TestProperties.withRetries(DELAYS, 0.2));

		for (int i = 0; i < 1000; i++) {
			assertThat(policy.delayBefore(2)).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(6));
		}
	}

	@Test
	void jitterActuallyVariesTheDelay() {
		RetryPolicy policy = new RetryPolicy(TestProperties.withRetries(DELAYS, 0.2));

		long distinctDelays = IntStream.range(0, 100)
				.mapToObj(i -> policy.delayBefore(2))
				.distinct()
				.count();

		assertThat(distinctDelays).isGreaterThan(1);
	}

}
