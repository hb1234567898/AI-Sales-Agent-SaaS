package com.yourcompany.salesagent.followup.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.followup.infrastructure.FollowUpRow;

public record FollowUpResponse(
		UUID id,
		UUID customerId,
		String customerName,
		UUID ownerMemberId,
		String ownerName,
		String status,
		Instant dueAt,
		int priority,
		String intentLevel,
		String riskLevel,
		String reason,
		String recommendedActionType,
		Map<String, Object> recommendedAction) {

	public static FollowUpResponse from(FollowUpRow row) {
		return new FollowUpResponse(
				row.getId(),
				row.getCustomerId(),
				row.getCustomerName(),
				row.getOwnerMemberId(),
				row.getOwnerName(),
				row.getStatus(),
				row.getDueAt(),
				row.getPriority(),
				row.getIntentLevel(),
				row.getRiskLevel(),
				row.getReason(),
				row.getRecommendedActionType(),
				row.getRecommendedAction());
	}
}
