package com.yourcompany.salesagent.approval.api;

import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
		Long expectedVersion,
		@Size(max = 500) String comment) {
}
