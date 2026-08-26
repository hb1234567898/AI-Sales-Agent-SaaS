package com.yourcompany.salesagent.interaction.api;

import java.time.Instant;

import com.yourcompany.salesagent.interaction.domain.ChatPlatform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatImportRequest(
		@NotNull ChatPlatform platform,
		@NotNull Instant occurredAt,
		@Size(max = 500) String subject,
		@NotBlank @Size(max = 100_000) String content,
		@Size(max = 220) String participantName) {
}
