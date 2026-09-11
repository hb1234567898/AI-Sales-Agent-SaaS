package com.yourcompany.salesagent.auth.security;

public class PasswordTransportException extends RuntimeException {

	public PasswordTransportException(String message) {
		super(message);
	}

	public PasswordTransportException(String message, Throwable cause) {
		super(message, cause);
	}
}
