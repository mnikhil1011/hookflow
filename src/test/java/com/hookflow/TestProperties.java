package com.hookflow;

import java.time.Duration;
import java.util.List;

import com.hookflow.config.HookflowProperties;
import com.hookflow.config.HookflowProperties.Delivery;
import com.hookflow.config.HookflowProperties.FlowControl;
import com.hookflow.config.HookflowProperties.FlowControl.Mode;
import com.hookflow.config.HookflowProperties.Polling;

/** Builds settings for unit tests, so each test states only the values it cares about. */
public final class TestProperties {

	private TestProperties() {
	}

	public static HookflowProperties withRetries(List<Duration> retryDelays, double jitter) {
		return properties(false, retryDelays, jitter);
	}

	public static HookflowProperties withInsecureUrlsAllowed(boolean allowed) {
		return properties(allowed, List.of(Duration.ofSeconds(5)), 0);
	}

	/** Flow-control settings with short timings, suitable for tests that wait for leases and pauses to end. */
	public static FlowControl flowControl(Mode mode, int startingLimit) {
		return new FlowControl(mode, startingLimit, startingLimit, 1, 200, 0.5,
				Duration.ofMillis(100), Duration.ofMillis(300), Duration.ofMillis(200), 1000);
	}

	private static HookflowProperties properties(boolean allowInsecureUrls, List<Duration> retryDelays, double jitter) {
		Delivery delivery = new Delivery(Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(60), 500,
				retryDelays, jitter);
		return new HookflowProperties("test-admin-token", allowInsecureUrls, delivery,
				flowControl(Mode.ADAPTIVE, 4), new Polling(Duration.ofMillis(200), 500));
	}

}
