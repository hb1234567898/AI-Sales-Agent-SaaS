package com.yourcompany.salesagent.assistant.api;

import java.time.Instant;
import java.util.UUID;

public record AssistantConversationResponse(
		UUID id,
		String title,
		String channel,
		String status,
		Instant lastMessageAt,
		Instant createdAt,
		Instant updatedAt) {
}
