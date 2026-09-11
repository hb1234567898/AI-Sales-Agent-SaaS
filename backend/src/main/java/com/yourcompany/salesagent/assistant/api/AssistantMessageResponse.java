package com.yourcompany.salesagent.assistant.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.assistant.api.AssistantChatResponse.AssistantToolTrace;

public record AssistantMessageResponse(
		UUID id,
		UUID conversationId,
		String role,
		String content,
		String reasoningSummary,
		List<AssistantToolTrace> toolTraces,
		Map<String, Object> data,
		Instant createdAt) {
}
