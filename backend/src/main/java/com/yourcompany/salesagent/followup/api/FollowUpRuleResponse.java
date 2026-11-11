package com.yourcompany.salesagent.followup.api;

import com.yourcompany.salesagent.followup.infrastructure.*;

public record FollowUpRuleResponse(boolean enabled, int inactiveDays, int runHour, int maxCustomers,
        boolean autoAnalyze, int reminderMinutes, long version, FollowUpAutomationRunRow lastRun) {
    public static FollowUpRuleResponse from(FollowUpRuleRow row, FollowUpAutomationRunRow lastRun) {
        return new FollowUpRuleResponse(row.isEnabled(), row.getInactiveDays(), row.getRunHour(), row.getMaxCustomers(),
                row.isAutoAnalyze(), row.getReminderMinutes(), row.getVersion(), lastRun);
    }
}
