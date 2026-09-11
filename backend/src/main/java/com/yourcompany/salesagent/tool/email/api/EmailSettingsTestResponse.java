package com.yourcompany.salesagent.tool.email.api;

public record EmailSettingsTestResponse(
		String status,
		String message,
		long latencyMs) {
}
