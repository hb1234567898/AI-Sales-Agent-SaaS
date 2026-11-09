package com.yourcompany.salesagent.opportunity.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.yourcompany.salesagent.opportunity.domain.*;
import com.yourcompany.salesagent.opportunity.infrastructure.OpportunityRow;

public record OpportunityResponse(UUID id, UUID customerId, String company, String name,
		UUID ownerMemberId, String ownerName, OpportunityStage stage, String status, BigDecimal amount,
		Integer probability, LocalDate expectedCloseDate, ForecastCategory forecastCategory,
		String nextStep, String closeReason, Instant stageChangedAt, Instant updatedAt, long version) {
	public static OpportunityResponse from(OpportunityRow row) {
		return new OpportunityResponse(row.getId(), row.getCustomerId(), row.getCompany(), row.getName(),
				row.getOwnerMemberId(), row.getOwnerName(), row.getStage(), row.getStatus(), row.getAmount(),
				row.getProbability(), row.getExpectedCloseDate(), row.getForecastCategory(), row.getNextStep(),
				row.getCloseReason(), row.getStageChangedAt(), row.getUpdatedAt(), row.getVersion());
	}
}
