package com.yourcompany.salesagent.followup.infrastructure;

import java.time.Instant;
import java.util.UUID;

public class FollowUpReminderRow {
    private UUID id;
    private UUID followUpId;
    private String customerName;
    private String title;
    private String kind;
    private Instant dueAt;
    private Instant createdAt;
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getFollowUpId() { return followUpId; }
    public void setFollowUpId(UUID followUpId) { this.followUpId = followUpId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
