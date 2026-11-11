package com.yourcompany.salesagent.followup.infrastructure;

import java.time.Instant;
import java.util.UUID;

public class FollowUpAutomationRunRow {
    private UUID id;
    private UUID organizationId;
    private UUID memberId;
    private String status;
    private int createdCount;
    private UUID agentRunId;
    private String errorMessage;
    private Instant createdAt;
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getMemberId() { return memberId; }
    public void setMemberId(UUID memberId) { this.memberId = memberId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }
    public UUID getAgentRunId() { return agentRunId; }
    public void setAgentRunId(UUID agentRunId) { this.agentRunId = agentRunId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
