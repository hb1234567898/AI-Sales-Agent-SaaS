package com.yourcompany.salesagent.ai.infrastructure;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.yourcompany.salesagent.ai.application.AiModelRuntimeConfiguration;
import com.yourcompany.salesagent.ai.application.ModelUsage;

@Component
public class AiOrchestrationClient {
	private final PythonAiClient pythonAiClient;

	public AiOrchestrationClient(PythonAiClient pythonAiClient) {
		this.pythonAiClient = pythonAiClient;
	}

	public reactor.core.publisher.Flux<String> streamAssistantReply(
			AiModelRuntimeConfiguration configuration, String request, String verifiedResult) {
		return streamAssistantReplyWithUsage(configuration, request, verifiedResult)
				.map(StreamChunk::text)
				.filter(text -> text != null && !text.isEmpty());
	}

	public reactor.core.publisher.Flux<StreamChunk> streamAssistantReplyWithUsage(
			AiModelRuntimeConfiguration configuration, String request, String verifiedResult) {
		return pythonAiClient.streamAssistantReply(configuration, request, verifiedResult)
				.map(event -> new StreamChunk(
						event.text(),
						event.usage() == null ? null : event.usage().toModelUsage(),
						event.providerRequestId(),
						event.model()));
	}

	public String testConnection(AiModelRuntimeConfiguration configuration) {
		return pythonAiClient.testConnection(configuration).content();
	}

	public ChatResult analyzeChat(AiModelRuntimeConfiguration configuration, String customerContext, String chatContent) {
		var response = pythonAiClient.analyzeChat(configuration, customerContext, chatContent);
		return new ChatResult(
				response.content(),
				response.usage() == null ? null : response.usage().toModelUsage(),
				response.providerRequestId(),
				response.model());
	}

	public AssistantPlan planAssistantAction(
			AiModelRuntimeConfiguration configuration,
			String userMessage,
			Map<String, Object> businessContext,
			List<Map<String, Object>> availableTools) {
		var response = pythonAiClient.planAgent(configuration, userMessage, businessContext, availableTools);
		var decision = response.decision();
		return new AssistantPlan(
				decision.intent(),
				decision.response(),
				decision.toolName(),
				decision.arguments() == null ? Map.of() : decision.arguments(),
				decision.confidence(),
				response.requiresApproval(),
				response.validationErrors() == null ? List.of() : response.validationErrors(),
				response.usage() == null ? null : response.usage().toModelUsage(),
				response.providerRequestId(),
				response.model());
	}

	public record ChatResult(String content, ModelUsage usage, String providerRequestId, String model) {
	}

	public record StreamChunk(String text, ModelUsage usage, String providerRequestId, String model) {
	}

	public record AssistantPlan(
			String intent,
			String response,
			String toolName,
			Map<String, Object> arguments,
			Double confidence,
			boolean requiresApproval,
			List<String> validationErrors,
			ModelUsage usage,
			String providerRequestId,
			String model) {
	}
}
