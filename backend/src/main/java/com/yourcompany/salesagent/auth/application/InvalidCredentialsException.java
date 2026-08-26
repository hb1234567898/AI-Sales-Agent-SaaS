package com.yourcompany.salesagent.auth.application;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("邮箱或密码不正确");
	}
}
