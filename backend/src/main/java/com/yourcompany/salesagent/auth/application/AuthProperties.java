package com.yourcompany.salesagent.auth.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
		String cookieName,
		boolean cookieSecure,
		Duration sessionDuration,
		Duration rememberDuration) {
}
