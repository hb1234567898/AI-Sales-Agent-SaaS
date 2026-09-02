package com.yourcompany.salesagent.tool.infrastructure;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * tool_invocation 表行映射。
 */
public class ToolInvocationRow {

	private UUID id;
	private UUID organizationId;
	private UUID actionRequestId;
	private UUID runId;
	private UUID stepId;
	private String toolName;
	private String toolVersion;
	private int attemptNo;
	private String idempotencyKey;
	private String status;
	private Map<String, Object> requestPayload;
	private Map<String, Object> responsePayload;
	private String externalOperationId;
	private boolean retryable;
	private String errorCode;
	private String errorMessage;
	private Long latencyMs;
	private Instant startedAt;
	private Instant completedAt;

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

	public UUID getActionRequestId() {
		return actionRequestId;
	}

	public void setActionRequestId(UUID actionRequestId) {
		this.actionRequestId = actionRequestId;
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

	public int getAttemptNo() {
		return attemptNo;
	}

	public void setAttemptNo(int attemptNo) {
		this.attemptNo = attemptNo;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Map<String, Object> getRequestPayload() {
		return requestPayload;
	}

	public void setRequestPayload(Map<String, Object> requestPayload) {
		this.requestPayload = requestPayload;
	}

	public Map<String, Object> getResponsePayload() {
		return responsePayload;
	}

	public void setResponsePayload(Map<String, Object> responsePayload) {
		this.responsePayload = responsePayload;
	}

	public String getExternalOperationId() {
		return externalOperationId;
	}

	public void setExternalOperationId(String externalOperationId) {
		this.externalOperationId = externalOperationId;
	}

	public boolean isRetryable() {
		return retryable;
	}

	public void setRetryable(boolean retryable) {
		this.retryable = retryable;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Long getLatencyMs() {
		return latencyMs;
	}

	public void setLatencyMs(Long latencyMs) {
		this.latencyMs = latencyMs;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}
}
