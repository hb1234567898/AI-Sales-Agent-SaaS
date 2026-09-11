package com.yourcompany.salesagent.assistant.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AssistantChatRequest(
		UUID conversationId,
		@NotBlank @Size(max = 120_000) String message,
		@Pattern(regexp = "WEB|DESKTOP", message = "会话来源只能是 WEB 或 DESKTOP") String channel) {
}
