package com.yourcompany.salesagent.ai.infrastructure;

import java.time.Instant;

public class ModelUsageSummaryRow {

	private long inputTokens;
	private long outputTokens;
	private long cachedInputTokens;
	private long successfulCalls;
	private Instant lastCalledAt;

	public long getInputTokens() { return inputTokens; }
	public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
	public long getOutputTokens() { return outputTokens; }
	public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
	public long getCachedInputTokens() { return cachedInputTokens; }
	public void setCachedInputTokens(long cachedInputTokens) { this.cachedInputTokens = cachedInputTokens; }
	public long getSuccessfulCalls() { return successfulCalls; }
	public void setSuccessfulCalls(long successfulCalls) { this.successfulCalls = successfulCalls; }
	public Instant getLastCalledAt() { return lastCalledAt; }
	public void setLastCalledAt(Instant lastCalledAt) { this.lastCalledAt = lastCalledAt; }
}
