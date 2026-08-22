package com.yourcompany.salesagent.system.api;

import java.time.Instant;

public record HealthResponse(
		String service,
		String status,
		Instant timestamp) {
}
