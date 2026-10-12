package com.hookflow;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for tests that need the whole application running against real Postgres, Redis and
 * Kafka. Every subclass shares one application and one set of containers, so they start only once.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

	@Value("${local.server.port}")
	private int port;

	protected ApiClient api;

	@BeforeEach
	protected void createApiClient() {
		api = new ApiClient(port);
	}

}
