package com.yourcompany.salesagent.assistant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantChatRequest(
		@NotBlank @Size(max = 120_000) String message) {
}
