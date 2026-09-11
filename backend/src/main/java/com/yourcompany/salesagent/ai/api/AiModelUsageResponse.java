package com.yourcompany.salesagent.ai.api;

import java.time.Instant;

public record AiModelUsageResponse(
		long inputTokens,
		long outputTokens,
		long cachedInputTokens,
		long totalTokens,
		long successfulCalls,
		Instant lastCalledAt,
		Long remainingTokens,
		String remainingStatus) {
}
