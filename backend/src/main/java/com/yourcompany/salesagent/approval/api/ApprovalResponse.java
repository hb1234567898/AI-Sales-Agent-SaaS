package com.yourcompany.salesagent.approval.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.approval.infrastructure.ApprovalRow;

public record ApprovalResponse(
		UUID id,
		UUID actionRequestId,
		UUID runId,
		UUID customerId,
		String customerName,
		String actionType,
		String riskLevel,
		String actionStatus,
		String failureCode,
		String failureMessage,
		String status,
		String reason,
		Map<String, Object> preview,
		String requester,
		Long version,
		Instant requestedAt,
		Instant expiresAt,
		Instant actionCompletedAt) {

	public static ApprovalResponse from(ApprovalRow row) {
		return new ApprovalResponse(
				row.getId(),
				row.getActionRequestId(),
				row.getRunId(),
				row.getCustomerId(),
				row.getCustomerName(),
				row.getActionType(),
				row.getRiskLevel(),
				row.getActionStatus(),
				row.getFailureCode(),
				row.getFailureMessage(),
				row.getStatus(),
				row.getReason(),
				row.getPreview(),
				row.getRequester(),
				row.getVersion(),
				row.getRequestedAt(),
				row.getExpiresAt(),
				row.getActionCompletedAt());
	}
}
