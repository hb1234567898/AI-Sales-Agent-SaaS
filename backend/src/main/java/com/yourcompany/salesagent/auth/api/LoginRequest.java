package com.yourcompany.salesagent.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "请输入邮箱")
		@Email(message = "请输入正确的邮箱地址")
		@Size(max = 320, message = "邮箱长度不能超过 320 个字符")
		String email,
		@NotBlank(message = "请输入密码")
		@Size(max = 200, message = "密码长度不能超过 200 个字符")
		String password,
		boolean rememberMe) {
}
