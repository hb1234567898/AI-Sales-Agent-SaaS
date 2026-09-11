package com.yourcompany.salesagent.tool.infrastructure;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * action_request 表行映射。仅承载执行层需要读取的字段，避免与审批/领域模型耦合。
 */
public class ActionRequestRow {

	private UUID id;
	private UUID organizationId;
	private UUID runId;
	private UUID stepId;
	private UUID customerId;
	private UUID requestedByMemberId;
	private String actionType;
	private String riskLevel;
	private String status;
	private String toolName;
	private String toolVersion;
	private String reason;
	private Map<String, Object> payload;
	private Map<String, Object> preview;
	private boolean requiresApproval;
	private String policyDecision;
	private String idempotencyKey;
	private Instant approvedAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public void setOrganizationId(UUID organizationId) {
		this.organizationId = organizationId;
	}

	public UUID getRunId() {
		return runId;
	}

	public void setRunId(UUID runId) {
		this.runId = runId;
	}

	public UUID getStepId() {
		return stepId;
	}

	public void setStepId(UUID stepId) {
		this.stepId = stepId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public void setCustomerId(UUID customerId) {
		this.customerId = customerId;
	}

	public UUID getRequestedByMemberId() {
		return requestedByMemberId;
	}

	public void setRequestedByMemberId(UUID requestedByMemberId) {
		this.requestedByMemberId = requestedByMemberId;
	}

	public String getActionType() {
		return actionType;
	}

	public void setActionType(String actionType) {
		this.actionType = actionType;
	}

	public String getRiskLevel() {
		return riskLevel;
	}

	public void setRiskLevel(String riskLevel) {
		this.riskLevel = riskLevel;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getToolName() {
		return toolName;
	}

	public void setToolName(String toolName) {
		this.toolName = toolName;
	}

	public String getToolVersion() {
		return toolVersion;
	}

	public void setToolVersion(String toolVersion) {
		this.toolVersion = toolVersion;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}

	public void setPayload(Map<String, Object> payload) {
		this.payload = payload;
	}

	public Map<String, Object> getPreview() {
		return preview;
	}

	public void setPreview(Map<String, Object> preview) {
		this.preview = preview;
	}

	public boolean isRequiresApproval() {
		return requiresApproval;
	}

	public void setRequiresApproval(boolean requiresApproval) {
		this.requiresApproval = requiresApproval;
	}

	public String getPolicyDecision() {
		return policyDecision;
	}

	public void setPolicyDecision(String policyDecision) {
		this.policyDecision = policyDecision;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

	public void setApprovedAt(Instant approvedAt) {
		this.approvedAt = approvedAt;
	}
}
