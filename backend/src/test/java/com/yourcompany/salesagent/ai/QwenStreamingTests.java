package com.yourcompany.salesagent.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.yourcompany.salesagent.ai.application.AiModelRuntimeConfiguration;
import com.yourcompany.salesagent.ai.infrastructure.PythonAiClient;
import com.yourcompany.salesagent.ai.infrastructure.PythonAiProperties;
import com.yourcompany.salesagent.ai.infrastructure.AiOrchestrationClient;

class QwenStreamingTests {
	@Test
	void receivesFirstDeltaBeforeUpstreamFinishes() throws Exception {
		var firstReceived = new CountDownLatch(1);
		var firstDeliveredBeforeFinish = new AtomicBoolean();
		var requestBody = new AtomicReference<String>();
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/stream", exchange -> {
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
			exchange.sendResponseHeaders(200, 0);
			try (var body = exchange.getResponseBody()) {
				body.write(chunk("你好").getBytes(StandardCharsets.UTF_8));
				body.flush();
				try { firstDeliveredBeforeFinish.set(firstReceived.await(5, TimeUnit.SECONDS)); }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
				body.write(chunk("世界").getBytes(StandardCharsets.UTF_8));
			}
		});
		server.start();
		try {
			var configuration = new AiModelRuntimeConfiguration("QWEN", "test-model",
					"https://model.example/v1", "test-only-key");
			var properties = new PythonAiProperties(
					"http://127.0.0.1:" + server.getAddress().getPort(),
					"internal-token",
					Duration.ofSeconds(2),
					Duration.ofSeconds(10));
			var output = new AiOrchestrationClient(new PythonAiClient(properties))
					.streamAssistantReply(configuration, "查看待审批", "没有待审批建议")
					.doOnNext(delta -> firstReceived.countDown()).collectList().block(Duration.ofSeconds(15));
			assertThat(firstDeliveredBeforeFinish.get()).isTrue();
			assertThat(String.join("", output)).isEqualTo("你好世界");
			assertThat(requestBody.get()).contains("\"user_request\":\"查看待审批\"");
		} finally {
			server.stop(0);
		}
	}

	private static String chunk(String text) {
		return "{\"type\":\"delta\",\"text\":\"" + text + "\",\"model\":\"test-model\"}\n";
	}
}
