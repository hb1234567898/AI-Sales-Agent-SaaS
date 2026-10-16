package com.yourcompany.salesagent.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TeamTokenBudgetRequest(
		@NotNull @PositiveOrZero Long totalTokens) {
}
