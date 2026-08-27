package com.yourcompany.salesagent.shared.security;

public class SecretEncryptionException extends RuntimeException {

	public SecretEncryptionException(String message) {
		super(message);
	}

	public SecretEncryptionException(String message, Throwable cause) {
		super(message, cause);
	}
}

