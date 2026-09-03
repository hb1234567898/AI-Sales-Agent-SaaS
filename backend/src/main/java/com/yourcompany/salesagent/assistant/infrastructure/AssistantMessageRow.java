package com.yourcompany.salesagent.assistant.infrastructure;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AssistantMessageRow(
		UUID id,
		UUID organizationId,
		UUID conversationId,
		String role,
		String content,
		String reasoningSummary,
		String toolTracesJson,
		Map<String, Object> data,
		Instant createdAt) {
}
