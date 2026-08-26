package com.yourcompany.salesagent.ai.api;

public record AiModelTestResponse(
		String provider,
		String model,
		String status,
		String responsePreview,
		long latencyMs) {
}
