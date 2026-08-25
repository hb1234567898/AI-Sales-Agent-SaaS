package com.yourcompany.salesagent.interaction.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.yourcompany.salesagent.interaction.domain.Interaction;
import com.yourcompany.salesagent.interaction.domain.InteractionDirection;
import com.yourcompany.salesagent.interaction.domain.InteractionType;

public record InteractionResponse(
		UUID id,
		UUID customerId,
		InteractionType type,
		InteractionDirection direction,
		Instant occurredAt,
		String subject,
		String bodyText,
		String bodyPreview,
		List<String> participants,
		String source,
		Instant createdAt) {

	public static InteractionResponse from(Interaction interaction) {
		return new InteractionResponse(
				interaction.getId(),
				interaction.getCustomerId(),
				interaction.getType(),
				interaction.getDirection(),
				interaction.getOccurredAt(),
				interaction.getSubject(),
				interaction.getBodyText(),
				interaction.getBodyPreview(),
				List.copyOf(interaction.getParticipants()),
				interaction.getSource(),
				interaction.getCreatedAt());
	}
}
