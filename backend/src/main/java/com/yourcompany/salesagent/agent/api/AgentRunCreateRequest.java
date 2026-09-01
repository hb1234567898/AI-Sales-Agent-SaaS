package com.yourcompany.salesagent.agent.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AgentRunCreateRequest(
		@Min(1) @Max(20) Integer maxCustomers,
		@Min(1) @Max(180) Integer recentDays,
		List<UUID> customerIds) {
}
