package com.yourcompany.salesagent.agent.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AgentStepRow {

	private UUID id;
	private UUID customerId;
	private long sequenceNo;
	private String stepType;
	private String name;
	private String status;
	private Map<String, Object> inputSnapshot = new HashMap<>();
	private Map<String, Object> outputSnapshot = new HashMap<>();
	private String errorMessage;
	private Instant startedAt;
	private Instant completedAt;
	private Long durationMs;

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getCustomerId() { return customerId; }
	public void setCustomerId(UUID customerId) { this.customerId = customerId; }
	public long getSequenceNo() { return sequenceNo; }
	public void setSequenceNo(long sequenceNo) { this.sequenceNo = sequenceNo; }
	public String getStepType() { return stepType; }
	public void setStepType(String stepType) { this.stepType = stepType; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public Map<String, Object> getInputSnapshot() { return inputSnapshot; }
	public void setInputSnapshot(Map<String, Object> inputSnapshot) { this.inputSnapshot = inputSnapshot; }
	public Map<String, Object> getOutputSnapshot() { return outputSnapshot; }
	public void setOutputSnapshot(Map<String, Object> outputSnapshot) { this.outputSnapshot = outputSnapshot; }
	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
	public Instant getStartedAt() { return startedAt; }
	public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
	public Long getDurationMs() { return durationMs; }
	public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
