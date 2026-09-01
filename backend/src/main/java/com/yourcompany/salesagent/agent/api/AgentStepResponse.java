package com.yourcompany.salesagent.agent.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.agent.infrastructure.AgentStepRow;

public record AgentStepResponse(
		UUID id,
		UUID customerId,
		long sequenceNo,
		String stepType,
		String name,
		String status,
		Map<String, Object> inputSnapshot,
		Map<String, Object> outputSnapshot,
		String errorMessage,
		Instant startedAt,
		Instant completedAt,
		Long durationMs) {

	public static AgentStepResponse from(AgentStepRow row) {
		return new AgentStepResponse(
				row.getId(),
				row.getCustomerId(),
				row.getSequenceNo(),
				row.getStepType(),
				row.getName(),
				row.getStatus(),
				row.getInputSnapshot(),
				row.getOutputSnapshot(),
				row.getErrorMessage(),
				row.getStartedAt(),
				row.getCompletedAt(),
				row.getDurationMs());
	}
}
