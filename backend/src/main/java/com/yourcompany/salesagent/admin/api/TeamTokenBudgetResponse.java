package com.yourcompany.salesagent.admin.api;

public record TeamTokenBudgetResponse(
		Long totalTokens,
		long allocatedTokens,
		Long unallocatedTokens) {
}
