package com.yourcompany.salesagent.assistant.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AssistantChatResponse(
		String role,
		String content,
		List<AssistantToolTrace> toolTraces,
		Map<String, Object> data,
		Instant createdAt) {

	public record AssistantToolTrace(
			String name,
			String status,
			String summary) {
	}
}
