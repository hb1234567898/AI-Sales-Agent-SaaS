package com.yourcompany.salesagent.followup.api;

import jakarta.validation.constraints.*;

public record FollowUpRuleRequest(
        boolean enabled,
        @Min(1) @Max(180) int inactiveDays,
        @Min(0) @Max(23) int runHour,
        @Min(1) @Max(20) int maxCustomers,
        boolean autoAnalyze,
        @Min(0) @Max(10080) int reminderMinutes,
        @Min(-1) long version) {
}
