package com.yourcompany.salesagent.customer.api;

import java.math.BigDecimal;

public record CustomerMetricsResponse(
		long total,
		long highIntent,
		long activeOpportunities,
		BigDecimal averageScore) {
}
