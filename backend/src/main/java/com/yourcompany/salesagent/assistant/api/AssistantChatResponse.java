package com.yourcompany.salesagent.assistant.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssistantChatResponse(
		UUID conversationId,
		UUID messageId,
		String role,
		String content,
		String reasoningSummary,
		List<AssistantToolTrace> toolTraces,
		Map<String, Object> data,
		Instant createdAt) {

	public record AssistantToolTrace(
			String name,
			String status,
			String summary) {
	}
}
