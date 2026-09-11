package com.yourcompany.salesagent.ai.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ModelCallRecordRequest(
		UUID organizationId,
		UUID runId,
		UUID stepId,
		UUID customerId,
		String purpose,
		String provider,
		String model,
		String providerRequestId,
		String promptVersion,
		String schemaVersion,
		String status,
		int attemptNo,
		ModelUsage usage,
		long latencyMs,
		String inputHash,
		String outputHash,
		Map<String, Object> inputSnapshot,
		Map<String, Object> outputSnapshot,
		String errorCode,
		String errorMessage,
		Instant startedAt,
		Instant completedAt) {
}
