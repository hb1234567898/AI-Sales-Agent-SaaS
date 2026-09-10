package com.yourcompany.salesagent.tool.email;

public record EmailRuntimeConfiguration(
		String host,
		int port,
		String username,
		String password,
		String fromAddress,
		boolean smtpAuth,
		boolean starttlsEnabled,
		boolean starttlsRequired) {
}
