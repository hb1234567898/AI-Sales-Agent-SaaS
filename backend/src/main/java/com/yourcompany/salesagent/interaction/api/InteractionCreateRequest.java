package com.yourcompany.salesagent.interaction.api;

import java.time.Instant;

import com.yourcompany.salesagent.interaction.domain.InteractionDirection;
import com.yourcompany.salesagent.interaction.domain.InteractionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InteractionCreateRequest(
		@NotNull InteractionType type,
		@NotNull InteractionDirection direction,
		@NotNull Instant occurredAt,
		@Size(max = 500) String subject,
		@NotBlank @Size(max = 50_000) String bodyText,
		@Size(max = 220) String participantName) {
}
