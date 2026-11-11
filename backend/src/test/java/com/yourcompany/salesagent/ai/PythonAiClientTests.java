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
	void sendsKnowledgeDocumentsAndQuestionsOverAuthenticatedHttp11() throws Exception {
		var bodies = new java.util.HashMap<String, String>();
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/knowledge/", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer knowledge-test");
			assertThat(exchange.getRequestHeaders().getFirst("Upgrade")).isNull();
			var path = exchange.getRequestURI().getPath();
			bodies.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			var response = path.endsWith("ingest")
					? "{\"chunks\":[{\"chunk_index\":0,\"page_number\":2,\"content\":\"审批原文\",\"search_terms\":\"审批\"}],\"character_count\":4}"
					: path.endsWith("terms") ? "{\"search_query\":\"报价 | 审批\"}"
					: "{\"content\":\"需要审批。[资料 1]\",\"sources\":[{\"id\":\"chunk-1\",\"title\":\"报价流程\",\"excerpt\":\"审批原文\",\"score\":0.8,\"metadata\":{}}],\"model\":\"test\",\"usage\":{\"input_tokens\":20,\"output_tokens\":10,\"total_tokens\":30}}";
			var bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
		});
		server.start();
		try {
			var client = new PythonAiClient(new PythonAiProperties("http://127.0.0.1:" + server.getAddress().getPort(), "knowledge-test", Duration.ofSeconds(2), Duration.ofSeconds(5)));
			assertThat(client.ingestDocument("流程.txt", "报价流程", "POLICY", "原文".getBytes(StandardCharsets.UTF_8)).chunks().get(0).pageNumber()).isEqualTo(2);
			assertThat(bodies.get("/v1/knowledge/ingest")).contains("content_base64").doesNotContain("api_key");
			assertThat(client.knowledgeSearchQuery("报价审批")).isEqualTo("报价 | 审批");
			var result = client.answerKnowledge(new AiModelRuntimeConfiguration("QWEN", "test", "https://example.invalid/v1", "key"), "报价审批", List.of(Map.of("id", "chunk-1", "title", "报价流程", "content", "审批原文")));
			assertThat(result.sources().get(0).id()).isEqualTo("chunk-1");
			assertThat(result.usage().toModelUsage().totalTokens()).isEqualTo(30);
			assertThat(bodies.get("/v1/knowledge/query")).contains("\"api_key\":\"key\"").contains("\"query\":\"报价审批\"");
		}
		finally { server.stop(0); }
	}

	@Test
	void forwardsDynamicConfigurationAndReadsStreamingUsage() throws Exception {
		var authorization = new AtomicReference<String>();
		var upgrade = new AtomicReference<String>();
		var requestBody = new AtomicReference<String>();
		var planUpgrade = new AtomicReference<String>();
		var planRequestBody = new AtomicReference<String>();
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/stream", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
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
			planUpgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
			planRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
			assertThat(upgrade.get()).isNull();
			assertThat(requestBody.get())
					.contains("\"api_key\":\"secret-key\"")
					.contains("\"verified_result\":\"已核实\"");

			var plan = client.planAgent(configuration, "新增客户云岚科技", Map.of(), List.of(Map.of(
					"name", "customer.create", "description", "新增客户", "risk_level", "LOW", "required_fields", List.of("name"))));
			assertThat(plan.decision().toolName()).isEqualTo("customer.create");
			assertThat(plan.decision().arguments()).containsEntry("name", "云岚科技");
			assertThat(plan.usage().toModelUsage().totalTokens()).isEqualTo(15);
			assertThat(planUpgrade.get()).isNull();
			assertThat(planRequestBody.get())
					.contains("\"api_key\":\"secret-key\"")
					.contains("\"user_message\":\"新增客户云岚科技\"");
		}
		finally {
			server.stop(0);
		}
	}
}
