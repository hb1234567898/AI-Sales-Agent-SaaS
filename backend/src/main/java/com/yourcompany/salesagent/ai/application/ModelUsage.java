package com.yourcompany.salesagent.ai.application;

public record ModelUsage(
		Integer promptTokens,
		Integer completionTokens,
		Integer totalTokens,
		Long cachedInputTokens,
		Object nativeUsage) {
}
