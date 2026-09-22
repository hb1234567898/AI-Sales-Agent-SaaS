package com.yourcompany.salesagent.lead.infrastructure;

public class LeadMetricsRow {
	private long pending;
	private long qualified;
	private long unassigned;
	private double averageScore;

	public long getPending() { return pending; }
	public void setPending(long pending) { this.pending = pending; }
	public long getQualified() { return qualified; }
	public void setQualified(long qualified) { this.qualified = qualified; }
	public long getUnassigned() { return unassigned; }
	public void setUnassigned(long unassigned) { this.unassigned = unassigned; }
	public double getAverageScore() { return averageScore; }
	public void setAverageScore(double averageScore) { this.averageScore = averageScore; }
}
