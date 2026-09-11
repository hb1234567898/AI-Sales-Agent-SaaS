package com.yourcompany.salesagent.agent.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AgentRunRow {

	private UUID id;
	private String name;
	private String triggerType;
	private String status;
	private LocalDate businessDate;
	private Map<String, Object> scope = new HashMap<>();
	private Map<String, Object> outputSummary = new HashMap<>();
	private int totalCandidates;
	private int processedCount;
	private int succeededCount;
	private int skippedCount;
	private int failedCount;
	private int pendingApprovalCount;
	private String errorMessage;
	private Instant queuedAt;
	private Instant startedAt;
	private Instant completedAt;
	private Instant createdAt;

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getTriggerType() { return triggerType; }
	public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public LocalDate getBusinessDate() { return businessDate; }
	public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
	public Map<String, Object> getScope() { return scope; }
	public void setScope(Map<String, Object> scope) { this.scope = scope; }
	public Map<String, Object> getOutputSummary() { return outputSummary; }
	public void setOutputSummary(Map<String, Object> outputSummary) { this.outputSummary = outputSummary; }
	public int getTotalCandidates() { return totalCandidates; }
	public void setTotalCandidates(int totalCandidates) { this.totalCandidates = totalCandidates; }
	public int getProcessedCount() { return processedCount; }
	public void setProcessedCount(int processedCount) { this.processedCount = processedCount; }
	public int getSucceededCount() { return succeededCount; }
	public void setSucceededCount(int succeededCount) { this.succeededCount = succeededCount; }
	public int getSkippedCount() { return skippedCount; }
	public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
	public int getFailedCount() { return failedCount; }
	public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
	public int getPendingApprovalCount() { return pendingApprovalCount; }
	public void setPendingApprovalCount(int pendingApprovalCount) { this.pendingApprovalCount = pendingApprovalCount; }
	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
	public Instant getQueuedAt() { return queuedAt; }
	public void setQueuedAt(Instant queuedAt) { this.queuedAt = queuedAt; }
	public Instant getStartedAt() { return startedAt; }
	public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
