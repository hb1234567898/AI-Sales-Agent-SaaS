package com.yourcompany.salesagent.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import org.springframework.util.StringUtils;

public record LoginRequest(
		@Email(message = "请输入正确的邮箱地址")
		@Size(max = 320, message = "邮箱长度不能超过 320 个字符")
		String email,
		@Size(max = 200, message = "密码长度不能超过 200 个字符")
		String password,
		@Size(max = 1024, message = "密码密文长度不能超过 1024 个字符")
		String passwordCiphertext,
		@Size(max = 120, message = "密码密钥版本长度不能超过 120 个字符")
		String passwordKeyId,
		boolean rememberMe) {

	@AssertTrue(message = "请输入邮箱")
	public boolean hasEmail() {
		return StringUtils.hasText(email);
	}

	@AssertTrue(message = "请输入密码")
	public boolean hasPasswordInput() {
		return StringUtils.hasText(password) || StringUtils.hasText(passwordCiphertext);
	}
}
