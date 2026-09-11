package com.yourcompany.salesagent.approval.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ApprovalRow {

	private UUID id;
	private UUID actionRequestId;
	private UUID runId;
	private UUID customerId;
	private String customerName;
	private String actionType;
	private String riskLevel;
	private String status;
	private String reason;
	private Map<String, Object> preview = new HashMap<>();
	private String requester;
	private Long version;
	private Instant requestedAt;
	private Instant expiresAt;

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getActionRequestId() { return actionRequestId; }
	public void setActionRequestId(UUID actionRequestId) { this.actionRequestId = actionRequestId; }
	public UUID getRunId() { return runId; }
	public void setRunId(UUID runId) { this.runId = runId; }
	public UUID getCustomerId() { return customerId; }
	public void setCustomerId(UUID customerId) { this.customerId = customerId; }
	public String getCustomerName() { return customerName; }
	public void setCustomerName(String customerName) { this.customerName = customerName; }
	public String getActionType() { return actionType; }
	public void setActionType(String actionType) { this.actionType = actionType; }
	public String getRiskLevel() { return riskLevel; }
	public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public Map<String, Object> getPreview() { return preview; }
	public void setPreview(Map<String, Object> preview) { this.preview = preview; }
	public String getRequester() { return requester; }
	public void setRequester(String requester) { this.requester = requester; }
	public Long getVersion() { return version; }
	public void setVersion(Long version) { this.version = version; }
	public Instant getRequestedAt() { return requestedAt; }
	public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
