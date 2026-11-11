package com.yourcompany.salesagent.followup.infrastructure;

import java.util.UUID;

public class FollowUpRuleRow {
    private UUID id;
    private UUID organizationId;
    private UUID memberId;
    private boolean enabled;
    private int inactiveDays = 7;
    private int runHour = 9;
    private int maxCustomers = 5;
    private boolean autoAnalyze;
    private int reminderMinutes = 30;
    private long version = -1;
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getMemberId() { return memberId; }
    public void setMemberId(UUID memberId) { this.memberId = memberId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getInactiveDays() { return inactiveDays; }
    public void setInactiveDays(int inactiveDays) { this.inactiveDays = inactiveDays; }
    public int getRunHour() { return runHour; }
    public void setRunHour(int runHour) { this.runHour = runHour; }
    public int getMaxCustomers() { return maxCustomers; }
    public void setMaxCustomers(int maxCustomers) { this.maxCustomers = maxCustomers; }
    public boolean isAutoAnalyze() { return autoAnalyze; }
    public void setAutoAnalyze(boolean autoAnalyze) { this.autoAnalyze = autoAnalyze; }
    public int getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
