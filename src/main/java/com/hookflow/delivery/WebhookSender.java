package com.hookflow.delivery;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import com.hookflow.config.HookflowProperties;
import org.springframework.stereotype.Component;

/** Makes the HTTP request for one attempt. It never throws: every failure becomes a {@link SendResult}. */
@Component
public class WebhookSender {

	private static final int MAX_SNIPPET_BYTES = 1024;

	private final HttpClient httpClient;
	private final Duration requestTimeout;
	private final WebhookSigner signer;

	public WebhookSender(HookflowProperties properties, WebhookSigner signer) {
		this.requestTimeout = properties.delivery().requestTimeout();
		this.signer = signer;
		this.httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(properties.delivery().connectTimeout())
				// A redirect could point at an internal address, so it is treated as a failed delivery.
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	public SendResult send(WebhookRequest request) {
		Instant startedAt = Instant.now();
		long startNanos = System.nanoTime();
		try {
			String timestamp = String.valueOf(startedAt.getEpochSecond());
			HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(request.url()))
					.timeout(requestTimeout)
					.header("Content-Type", "application/json")
					.header("User-Agent", "Hookflow/1.0")
					.header("webhook-id", request.webhookId())
					.header("webhook-timestamp", timestamp)
					.header("webhook-signature",
							signer.sign(request.signingSecret(), request.webhookId(), timestamp, request.body()))
					.POST(HttpRequest.BodyPublishers.ofString(request.body()))
					.build();

			HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
			String snippet = readSnippet(response.body());
			return SendResult.responded(startedAt, elapsedSince(startNanos), response.statusCode(), snippet);
		}
		catch (HttpTimeoutException e) {
			return SendResult.failed(startedAt, elapsedSince(startNanos), "TIMEOUT");
		}
		catch (IOException | IllegalArgumentException e) {
			return SendResult.failed(startedAt, elapsedSince(startNanos), "CONNECTION_ERROR");
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return SendResult.failed(startedAt, elapsedSince(startNanos), "INTERRUPTED");
		}
	}

	/** Reads only the start of the response, so a huge body cannot exhaust memory. */
	private static String readSnippet(InputStream body) {
		try (body) {
			return new String(body.readNBytes(MAX_SNIPPET_BYTES), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			return "";
		}
	}

	private static Duration elapsedSince(long startNanos) {
		return Duration.ofNanos(System.nanoTime() - startNanos);
	}

}
