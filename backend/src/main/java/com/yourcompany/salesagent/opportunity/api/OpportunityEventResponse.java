package com.yourcompany.salesagent.opportunity.api;

import java.time.Instant;
import java.util.UUID;

public record OpportunityEventResponse(UUID id, String actorName, String fromStage, String toStage,
		String summary, Instant createdAt) {}
