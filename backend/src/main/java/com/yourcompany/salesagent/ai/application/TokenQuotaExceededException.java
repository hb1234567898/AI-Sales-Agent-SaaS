package com.yourcompany.salesagent.ai.application;

public class TokenQuotaExceededException extends RuntimeException {

	public TokenQuotaExceededException(String message) {
		super(message);
	}
}
