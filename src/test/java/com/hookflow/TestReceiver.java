package com.hookflow;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny HTTP server that plays the part of a customer's endpoint in tests. It records every request
 * it gets, and each path can be told which statuses to answer with.
 */
public class TestReceiver implements AutoCloseable {

	public record ReceivedWebhook(String id, String timestamp, String signature, String body) {
	}

	private final HttpServer server;
	private final Map<String, List<ReceivedWebhook>> receivedByPath = new ConcurrentHashMap<>();
	private final Map<String, Deque<Integer>> plannedStatuses = new ConcurrentHashMap<>();
	private final Map<String, Integer> fixedStatuses = new ConcurrentHashMap<>();

	public TestReceiver() {
		try {
			server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not start the test receiver", e);
		}
		server.createContext("/", this::handle);
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();
	}

	public String url(String path) {
		return "http://localhost:" + server.getAddress().getPort() + path;
	}

	/** Answers the next requests to this path with these statuses, in order. After that it answers 200. */
	public void respondWith(String path, Integer... statuses) {
		plannedStatuses.put(path, new ConcurrentLinkedDeque<>(List.of(statuses)));
	}

	/** Answers every request to this path with this status until told otherwise. */
	public void alwaysRespondWith(String path, int status) {
		fixedStatuses.put(path, status);
	}

	public List<ReceivedWebhook> receivedAt(String path) {
		return receivedByPath.getOrDefault(path, List.of());
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		receivedByPath.computeIfAbsent(path, key -> new CopyOnWriteArrayList<>())
				.add(new ReceivedWebhook(
						exchange.getRequestHeaders().getFirst("webhook-id"),
						exchange.getRequestHeaders().getFirst("webhook-timestamp"),
						exchange.getRequestHeaders().getFirst("webhook-signature"),
						body));

		exchange.sendResponseHeaders(nextStatus(path), -1);
		exchange.close();
	}

	private int nextStatus(String path) {
		Integer planned = plannedStatuses.getOrDefault(path, new ConcurrentLinkedDeque<>()).poll();
		if (planned != null) {
			return planned;
		}
		return fixedStatuses.getOrDefault(path, 200);
	}

}
