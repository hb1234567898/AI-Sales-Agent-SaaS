package com.yourcompany.salesagent.assistant.infrastructure;

import java.time.Instant;
import java.util.UUID;

public record AssistantConversationRow(
		UUID id,
		UUID organizationId,
		UUID userId,
		UUID memberId,
		String title,
		String channel,
		String status,
		Instant lastMessageAt,
		Instant createdAt,
		Instant updatedAt,
		Integer version) {
}
