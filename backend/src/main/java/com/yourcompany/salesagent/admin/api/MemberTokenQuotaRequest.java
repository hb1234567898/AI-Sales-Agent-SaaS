package com.yourcompany.salesagent.admin.api;

import jakarta.validation.constraints.PositiveOrZero;

public record MemberTokenQuotaRequest(
		@PositiveOrZero(message = "Token 额度不能小于 0")
		Long allocatedTokens) {
}
