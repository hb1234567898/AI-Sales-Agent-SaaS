package com.yourcompany.salesagent.auth.application;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Refresh Token 无效或已经失效，请重新登录");
	}
}
