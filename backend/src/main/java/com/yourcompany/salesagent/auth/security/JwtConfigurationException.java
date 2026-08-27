package com.yourcompany.salesagent.auth.security;

public class JwtConfigurationException extends RuntimeException {

	public JwtConfigurationException(String message) {
		super(message);
	}

	public JwtConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}
}
