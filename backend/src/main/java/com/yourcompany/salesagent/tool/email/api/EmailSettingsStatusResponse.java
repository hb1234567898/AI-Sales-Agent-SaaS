package com.yourcompany.salesagent.tool.email.api;

public record EmailSettingsStatusResponse(
		String host,
		Integer port,
		String username,
		String fromAddress,
		boolean smtpAuth,
		boolean starttlsEnabled,
		boolean starttlsRequired,
		boolean passwordConfigured,
		boolean ready,
		String status) {
}
