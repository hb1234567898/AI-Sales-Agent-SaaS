package com.yourcompany.salesagent.agent.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.agent.infrastructure.AgentRunRow;

public record AgentRunResponse(
		UUID id,
		String name,
		String triggerType,
		String status,
		LocalDate businessDate,
		Map<String, Object> scope,
		Map<String, Object> outputSummary,
		int totalCandidates,
		int processedCount,
		int succeededCount,
		int skippedCount,
		int failedCount,
		int pendingApprovalCount,
		String errorMessage,
		Instant queuedAt,
		Instant startedAt,
		Instant completedAt,
		Instant createdAt) {

	public static AgentRunResponse from(AgentRunRow row) {
		return new AgentRunResponse(
				row.getId(),
				row.getName(),
				row.getTriggerType(),
				row.getStatus(),
				row.getBusinessDate(),
				row.getScope(),
				row.getOutputSummary(),
				row.getTotalCandidates(),
				row.getProcessedCount(),
				row.getSucceededCount(),
				row.getSkippedCount(),
				row.getFailedCount(),
				row.getPendingApprovalCount(),
				row.getErrorMessage(),
				row.getQueuedAt(),
				row.getStartedAt(),
				row.getCompletedAt(),
				row.getCreatedAt());
	}
}
