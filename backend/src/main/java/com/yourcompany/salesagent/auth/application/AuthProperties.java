package com.yourcompany.salesagent.auth.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
		Duration accessTokenDuration,
		Duration sessionDuration,
		Duration rememberDuration,
		String jwtIssuer,
		String jwtSigningKey) {
}
