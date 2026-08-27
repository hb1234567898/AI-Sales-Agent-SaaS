package com.yourcompany.salesagent.ai.application;

public record AiModelRuntimeConfiguration(
		String provider,
		String model,
		String baseUrl,
		String apiKey) {
}

