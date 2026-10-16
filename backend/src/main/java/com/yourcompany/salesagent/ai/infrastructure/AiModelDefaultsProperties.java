package com.yourcompany.salesagent.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.defaults")
public record AiModelDefaultsProperties(String baseUrl, String model) {
}
