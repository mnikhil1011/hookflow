import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A stand-in for customers' webhook endpoints, for demos and load tests. It needs only a JDK:
 *
 *     java mock-receiver/MockReceiver.java [port]        (default port 9000)
 *
 * Every path under /hook/ acts as a separate endpoint:
 *
 *     POST /hook/{name}                                   receives a webhook
 *     POST /admin/behavior?path=/hook/1&delayMs=8000&status=200
 *                                                         makes one path slow or failing
 *     GET  /admin/stats                                   per path: webhooks received, duplicates and
 *                                                         end-to-end delivery time (median and p99)
 *     POST /admin/reset                                   clears behaviours and statistics
 */
public class MockReceiver {

	private record Behavior(long delayMs, int status) {
	}

	/** What one path has received so far. */
	private static class PathStats {

		final Set<String> webhookIds = ConcurrentHashMap.newKeySet();
		final List<Long> deliveryMillis = Collections.synchronizedList(new ArrayList<>());
		int received;
		int duplicates;

		synchronized void record(String webhookId, Long endToEndMillis) {
			received++;
			if (webhookId != null && !webhookIds.add(webhookId)) {
				duplicates++;
			}
			if (endToEndMillis != null) {
				deliveryMillis.add(endToEndMillis);
			}
		}
	}

	private static final Behavior HEALTHY = new Behavior(0, 200);
	private static final Pattern EVENT_TIMESTAMP = Pattern.compile("\"timestamp\":\"([^\"]+)\"");

	private static final Map<String, Behavior> behaviors = new ConcurrentHashMap<>();
	private static final Map<String, PathStats> stats = new ConcurrentHashMap<>();

	public static void main(String[] args) throws IOException {
		int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
		HttpServer server = HttpServer.create(new InetSocketAddress(port), 1000);
		server.createContext("/hook/", MockReceiver::receiveWebhook);
		server.createContext("/admin/behavior", MockReceiver::setBehavior);
		server.createContext("/admin/stats", MockReceiver::showStats);
		server.createContext("/admin/reset", MockReceiver::reset);
		// Virtual threads let thousands of deliberately slow requests wait at the same time.
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();
		System.out.println("Mock receiver listening on http://localhost:" + port);
	}

	private static void receiveWebhook(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		Behavior behavior = behaviors.getOrDefault(path, HEALTHY);

		sleep(behavior.delayMs());
		if (behavior.status() >= 200 && behavior.status() < 300) {
			String webhookId = exchange.getRequestHeaders().getFirst("webhook-id");
			stats.computeIfAbsent(path, key -> new PathStats()).record(webhookId, millisSinceEventWasAccepted(body));
		}
		respond(exchange, behavior.status(), "");
	}

	private static void setBehavior(HttpExchange exchange) throws IOException {
		Map<String, String> query = queryOf(exchange);
		String path = query.get("path");
		if (path == null) {
			respond(exchange, 400, "The 'path' query parameter is required, for example path=/hook/1\n");
			return;
		}
		long delayMs = Long.parseLong(query.getOrDefault("delayMs", "0"));
		int status = Integer.parseInt(query.getOrDefault("status", "200"));
		behaviors.put(path, new Behavior(delayMs, status));
		respond(exchange, 200, path + " now answers " + status + " after " + delayMs + " ms\n");
	}

	private static void showStats(HttpExchange exchange) throws IOException {
		StringBuilder json = new StringBuilder("{\n");
		Map<String, PathStats> sorted = new TreeMap<>(stats);
		int index = 0;
		for (Map.Entry<String, PathStats> entry : sorted.entrySet()) {
			PathStats pathStats = entry.getValue();
			List<Long> times;
			synchronized (pathStats.deliveryMillis) {
				times = new ArrayList<>(pathStats.deliveryMillis);
			}
			Collections.sort(times);
			json.append(String.format("  \"%s\": {\"received\": %d, \"duplicates\": %d, \"medianMs\": %d, \"p99Ms\": %d}%s\n",
					entry.getKey(), pathStats.received, pathStats.duplicates, percentile(times, 0.50),
					percentile(times, 0.99), ++index < sorted.size() ? "," : ""));
		}
		respond(exchange, 200, json.append("}\n").toString());
	}

	private static void reset(HttpExchange exchange) throws IOException {
		behaviors.clear();
		stats.clear();
		respond(exchange, 200, "Behaviours and statistics cleared\n");
	}

	/** Hookflow puts the time the event was accepted into the body, so the receiver can measure the whole trip. */
	private static Long millisSinceEventWasAccepted(String body) {
		Matcher matcher = EVENT_TIMESTAMP.matcher(body);
		if (!matcher.find()) {
			return null;
		}
		return Duration.between(Instant.parse(matcher.group(1)), Instant.now()).toMillis();
	}

	private static long percentile(List<Long> sortedValues, double fraction) {
		if (sortedValues.isEmpty()) {
			return 0;
		}
		int index = (int) Math.ceil(fraction * sortedValues.size()) - 1;
		return sortedValues.get(Math.max(index, 0));
	}

	private static Map<String, String> queryOf(HttpExchange exchange) {
		Map<String, String> query = new HashMap<>();
		String raw = exchange.getRequestURI().getRawQuery();
		if (raw != null) {
			for (String pair : raw.split("&")) {
				String[] parts = pair.split("=", 2);
				query.put(parts[0], parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
			}
		}
		return query;
	}

	private static void sleep(long millis) {
		if (millis <= 0) {
			return;
		}
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			exchange.getResponseBody().write(bytes);
		}
		exchange.close();
	}

}
