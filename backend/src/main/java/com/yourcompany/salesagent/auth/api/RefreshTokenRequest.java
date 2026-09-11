package com.yourcompany.salesagent.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
		@NotBlank(message = "Refresh Token 不能为空")
		@Size(max = 4096, message = "Refresh Token 格式无效")
		String refreshToken) {
}
