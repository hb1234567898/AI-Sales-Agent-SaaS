package com.yourcompany.salesagent.lead.api;

public record LeadMetricsResponse(long pending, long qualified, long unassigned, double averageScore) {
}
