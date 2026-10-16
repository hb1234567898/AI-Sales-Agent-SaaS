package com.yourcompany.salesagent.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import com.yourcompany.salesagent.ai.application.AiModelRuntimeConfiguration;
import com.yourcompany.salesagent.ai.infrastructure.PythonAiClient;
import com.yourcompany.salesagent.ai.infrastructure.PythonAiProperties;

class PythonAiClientTests {

	@Test
	void forwardsDynamicConfigurationAndReadsStreamingUsage() throws Exception {
		var authorization = new AtomicReference<String>();
		var requestBody = new AtomicReference<String>();
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/stream", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			var body = """
					{"type":"delta","text":"你好","provider_request_id":"req-1","model":"test-model"}
					{"type":"usage","usage":{"input_tokens":4,"output_tokens":2,"total_tokens":6,"cached_input_tokens":1},"model":"test-model"}
					""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.createContext("/v1/agent/plan", exchange -> {
			var body = """
					{"decision":{"intent":"BUSINESS_ACTION","response":"","tool_name":"customer.create","arguments":{"name":"云岚科技"},"confidence":0.96},"requires_approval":false,"validation_errors":[],"usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15,"cached_input_tokens":0},"provider_request_id":"plan-1","model":"test-model"}
					""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			var properties = new PythonAiProperties(
					"http://127.0.0.1:" + server.getAddress().getPort(),
					"internal-token",
					Duration.ofSeconds(2),
					Duration.ofSeconds(5));
			var client = new PythonAiClient(properties);
			var configuration = new AiModelRuntimeConfiguration(
					"QWEN", "test-model", "https://model.example/v1", "secret-key");

			var events = client.streamAssistantReply(configuration, "请求", "已核实")
					.collectList()
					.block(Duration.ofSeconds(10));

			assertThat(events).hasSize(2);
			assertThat(events.get(0).text()).isEqualTo("你好");
			assertThat(events.get(1).usage().toModelUsage().totalTokens()).isEqualTo(6);
			assertThat(authorization.get()).isEqualTo("Bearer internal-token");
			assertThat(requestBody.get())
					.contains("\"api_key\":\"secret-key\"")
					.contains("\"verified_result\":\"已核实\"");

			var plan = client.planAgent(configuration, "新增客户云岚科技", Map.of(), List.of(Map.of(
					"name", "customer.create", "description", "新增客户", "risk_level", "LOW", "required_fields", List.of("name"))));
			assertThat(plan.decision().toolName()).isEqualTo("customer.create");
			assertThat(plan.decision().arguments()).containsEntry("name", "云岚科技");
			assertThat(plan.usage().toModelUsage().totalTokens()).isEqualTo(15);
		}
		finally {
			server.stop(0);
		}
	}
}
