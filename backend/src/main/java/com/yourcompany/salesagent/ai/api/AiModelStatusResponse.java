package com.yourcompany.salesagent.ai.api;

public record AiModelStatusResponse(
		String provider,
		String model,
		String baseUrl,
		boolean apiKeyConfigured,
		boolean ready,
		String status) {
}
