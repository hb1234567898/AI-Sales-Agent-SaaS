package com.yourcompany.salesagent.opportunity.api;

import java.math.BigDecimal;

public record OpportunityMetricsResponse(long activeCount, BigDecimal pipelineAmount,
		BigDecimal weightedAmount, long stalledCount) {}
