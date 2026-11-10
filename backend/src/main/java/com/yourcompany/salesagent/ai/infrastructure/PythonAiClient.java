package com.yourcompany.salesagent.ai.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.yourcompany.salesagent.ai.application.AiModelRuntimeConfiguration;
import com.yourcompany.salesagent.ai.application.ModelUsage;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PythonAiClient {

	private final PythonAiProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public PythonAiClient(PythonAiProperties properties) {
		this.properties = properties;
		this.objectMapper = JsonMapper.builder().build();
		this.httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(properties.connectTimeout())
				.build();
	}

	public Flux<PythonStreamEvent> streamAssistantReply(
			AiModelRuntimeConfiguration configuration, String request, String verifiedResult) {
		var payload = modelPayload(configuration);
		payload.put("user_request", request);
		payload.put("verified_result", verifiedResult);
		return Flux.using(
				() -> sendLines("/v1/chat/stream", payload),
				lines -> Flux.fromStream(lines)
						.filter(line -> !line.isBlank())
						.map(this::readStreamEvent),
				Stream::close)
				.subscribeOn(Schedulers.boundedElastic());
	}

	public PythonModelResponse testConnection(AiModelRuntimeConfiguration configuration) {
		return post("/v1/model/test", modelPayload(configuration));
	}

	public PythonModelResponse analyzeChat(
			AiModelRuntimeConfiguration configuration, String customerContext, String chatContent) {
		var payload = modelPayload(configuration);
		payload.put("customer_context", customerContext);
		payload.put("chat_content", chatContent);
		return post("/v1/chat/analyze", payload);
	}

	public PythonAgentPlan planAgent(
			AiModelRuntimeConfiguration configuration,
			String userMessage,
			Map<String, Object> businessContext,
			List<Map<String, Object>> availableTools) {
		var payload = modelPayload(configuration);
		payload.put("user_message", userMessage);
		payload.put("business_context", businessContext);
		payload.put("available_tools", availableTools);
		return post("/v1/agent/plan", payload, PythonAgentPlan.class);
	}

	public PythonDocument ingestDocument(String filename, String title, String category, byte[] content) {
		return post("/v1/knowledge/ingest", Map.of("filename", filename, "title", title, "category", category,
				"content_base64", java.util.Base64.getEncoder().encodeToString(content)), PythonDocument.class);
	}

	public String knowledgeSearchQuery(String query) {
		return post("/v1/knowledge/terms", Map.of("query", query), PythonSearchTerms.class).searchQuery();
	}

	public PythonKnowledgeAnswer answerKnowledge(AiModelRuntimeConfiguration configuration, String query,
			List<Map<String, Object>> candidates) {
		var payload = modelPayload(configuration);
		payload.put("query", query);
		payload.put("candidates", candidates);
		payload.put("top_k", 5);
		return post("/v1/knowledge/query", payload, PythonKnowledgeAnswer.class);
	}

	private Stream<String> sendLines(String path, Object payload) {
		try {
			var response = httpClient.send(request(path, payload), HttpResponse.BodyHandlers.ofLines());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				response.body().close();
				throw new PythonAiException("Python AI service returned status " + response.statusCode());
			}
			return response.body();
		}
		catch (IOException | JacksonException exception) {
			throw new PythonAiException("Cannot reach Python AI service", exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new PythonAiException("Python AI service request was interrupted", exception);
		}
	}

	private PythonModelResponse post(String path, Object payload) {
		return post(path, payload, PythonModelResponse.class);
	}

	private <T> T post(String path, Object payload, Class<T> responseType) {
		try {
			var response = httpClient.send(request(path, payload), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				if (path.equals("/v1/knowledge/ingest") && response.statusCode() == 400) {
					var detail = objectMapper.readTree(response.body()).path("detail").asText("");
					throw new PythonAiException("Document: " + detail.substring(0, Math.min(detail.length(), 1000)));
				}
				throw new PythonAiException("Python AI service returned status " + response.statusCode());
			}
			return objectMapper.readValue(response.body(), responseType);
		}
		catch (IOException | JacksonException exception) {
			throw new PythonAiException("Cannot read Python AI service response", exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new PythonAiException("Python AI service request was interrupted", exception);
		}
	}

	private HttpRequest request(String path, Object payload) throws JacksonException {
		var builder = HttpRequest.newBuilder()
				.uri(URI.create(properties.baseUrl() + path))
				.timeout(properties.readTimeout())
				.header("Content-Type", "application/json")
				.header("Accept", "application/json, application/x-ndjson")
				.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
		if (properties.serviceToken() != null && !properties.serviceToken().isBlank()) {
			builder.header("Authorization", "Bearer " + properties.serviceToken());
		}
		return builder.build();
	}

	private java.util.HashMap<String, Object> modelPayload(AiModelRuntimeConfiguration configuration) {
		return new java.util.HashMap<>(Map.of(
				"api_key", configuration.apiKey(),
				"base_url", configuration.baseUrl(),
				"model", configuration.model()));
	}

	private PythonStreamEvent readStreamEvent(String line) {
		try {
			return objectMapper.readValue(line, PythonStreamEvent.class);
		}
		catch (JacksonException exception) {
			throw new PythonAiException("Python AI service returned invalid stream data", exception);
		}
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonDocument(List<PythonDocumentChunk> chunks, int characterCount) {}
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonDocumentChunk(int chunkIndex, Integer pageNumber, String content, String searchTerms) {}
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonSearchTerms(String searchQuery) {}
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonKnowledgeSource(String id, String title, String excerpt, double score, Map<String, Object> metadata) {}
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonKnowledgeAnswer(String content, PythonUsage usage, String providerRequestId, String model,
			List<PythonKnowledgeSource> sources) {}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonModelResponse(
			String content,
			PythonUsage usage,
			String providerRequestId,
			String model) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonAgentPlan(
			PythonAgentDecision decision,
			boolean requiresApproval,
			List<String> validationErrors,
			PythonUsage usage,
			String providerRequestId,
			String model) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonAgentDecision(
			String intent,
			String response,
			String toolName,
			Map<String, Object> arguments,
			Double confidence) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonStreamEvent(
			String type,
			String text,
			PythonUsage usage,
			String providerRequestId,
			String model) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PythonUsage(
			Integer inputTokens,
			Integer outputTokens,
			Integer totalTokens,
			Long cachedInputTokens) {

		public ModelUsage toModelUsage() {
			return new ModelUsage(inputTokens, outputTokens, totalTokens, cachedInputTokens, null);
		}
	}

	public static class PythonAiException extends RuntimeException {
		public PythonAiException(String message) {
			super(message);
		}

		public PythonAiException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
