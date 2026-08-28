package com.yourcompany.salesagent.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.qwen")
public record QwenModelProperties(String baseUrl, String model) {
}
