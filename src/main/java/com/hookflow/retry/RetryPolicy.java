package com.hookflow.retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.hookflow.config.HookflowProperties;
import org.springframework.stereotype.Component;

/**
 * Decides whether a failed delivery gets another attempt, and how long to wait first.
 * <p>
 * The waits come from configuration and grow with each attempt, giving a broken receiver time to
 * recover. Each wait is varied randomly (jitter), so deliveries that failed together do not all
 * retry at the same instant.
 */
@Component
public class RetryPolicy {

	private final List<Duration> delays;
	private final double jitter;

	public RetryPolicy(HookflowProperties properties) {
		this.delays = properties.delivery().retryDelays();
		this.jitter = properties.delivery().retryJitter();
	}

	/** The first attempt plus one retry per configured delay. */
	public int maxAttempts() {
		return delays.size() + 1;
	}

	public boolean hasAttemptsLeft(int attemptsMade) {
		return attemptsMade < maxAttempts();
	}

	/**
	 * @param attemptNumber the attempt about to be scheduled; 2 is the first retry
	 * @return how long to wait before that attempt
	 */
	public Duration delayBefore(int attemptNumber) {
		Duration base = delays.get(attemptNumber - 2);
		double variation = 1 + jitter * (2 * ThreadLocalRandom.current().nextDouble() - 1);
		return Duration.ofMillis(Math.round(base.toMillis() * variation));
	}

}
