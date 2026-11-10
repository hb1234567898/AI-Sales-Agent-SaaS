package com.yourcompany.salesagent.ai.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.python")
public record PythonAiProperties(
		String baseUrl,
		String serviceToken,
		Duration connectTimeout,
		Duration readTimeout) {
}
