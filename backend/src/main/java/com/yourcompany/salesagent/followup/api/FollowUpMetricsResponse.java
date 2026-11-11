package com.yourcompany.salesagent.followup.api;

public record FollowUpMetricsResponse(long pending, long today, long overdue, long completed) {
}
