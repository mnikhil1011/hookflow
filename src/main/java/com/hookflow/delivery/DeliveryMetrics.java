package com.hookflow.delivery;

import java.time.Duration;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** The application's own metrics, exposed to Prometheus at /actuator/prometheus. */
@Component
public class DeliveryMetrics {

	private final MeterRegistry registry;

	public DeliveryMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void eventAccepted() {
		registry.counter("hookflow.events.accepted").increment();
	}

	/** Records one HTTP attempt: how long the receiver took, and how it ended. */
	public void attempt(AttemptOutcome outcome, Duration httpDuration) {
		Timer.builder("hookflow.delivery.http.duration")
				.description("Time the receiver took to answer")
				.tag("outcome", outcome.name().toLowerCase())
				.publishPercentileHistogram()
				.register(registry)
				.record(httpDuration);
	}

	/**
	 * Records the time from an event being accepted to its delivery succeeding.
	 * <p>
	 * The endpoint tag makes it possible to compare healthy and struggling endpoints. It suits a
	 * demo with a few hundred endpoints; with millions it would create too many time series.
	 */
	public void delivered(UUID endpointId, Duration endToEnd) {
		Timer.builder("hookflow.delivery.end_to_end")
				.description("Time from event accepted to delivered")
				.tag("endpoint", endpointId.toString())
				.publishPercentileHistogram()
				.register(registry)
				.record(endToEnd);
	}

	public void dead() {
		registry.counter("hookflow.deliveries.dead").increment();
	}

	/** Counts a delivery that found no free slot and had to wait. */
	public void parked() {
		registry.counter("hookflow.flowcontrol.parked").increment();
	}

}
