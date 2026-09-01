package com.yourcompany.salesagent.followup.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FollowUpRow {

	private UUID id;
	private UUID customerId;
	private String customerName;
	private UUID ownerMemberId;
	private String ownerName;
	private String status;
	private Instant dueAt;
	private int priority;
	private String intentLevel;
	private String riskLevel;
	private String reason;
	private String recommendedActionType;
	private Map<String, Object> recommendedAction = new HashMap<>();

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getCustomerId() { return customerId; }
	public void setCustomerId(UUID customerId) { this.customerId = customerId; }
	public String getCustomerName() { return customerName; }
	public void setCustomerName(String customerName) { this.customerName = customerName; }
	public UUID getOwnerMemberId() { return ownerMemberId; }
	public void setOwnerMemberId(UUID ownerMemberId) { this.ownerMemberId = ownerMemberId; }
	public String getOwnerName() { return ownerName; }
	public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public Instant getDueAt() { return dueAt; }
	public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
	public int getPriority() { return priority; }
	public void setPriority(int priority) { this.priority = priority; }
	public String getIntentLevel() { return intentLevel; }
	public void setIntentLevel(String intentLevel) { this.intentLevel = intentLevel; }
	public String getRiskLevel() { return riskLevel; }
	public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public String getRecommendedActionType() { return recommendedActionType; }
	public void setRecommendedActionType(String recommendedActionType) { this.recommendedActionType = recommendedActionType; }
	public Map<String, Object> getRecommendedAction() { return recommendedAction; }
	public void setRecommendedAction(Map<String, Object> recommendedAction) { this.recommendedAction = recommendedAction; }
}
