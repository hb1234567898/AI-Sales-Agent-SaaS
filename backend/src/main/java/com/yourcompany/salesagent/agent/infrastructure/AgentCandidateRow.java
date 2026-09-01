package com.yourcompany.salesagent.agent.infrastructure;

import java.time.Instant;
import java.util.UUID;

public class AgentCandidateRow {

	private UUID customerId;
	private String customerName;
	private UUID ownerMemberId;
	private UUID interactionId;
	private Instant interactionAt;
	private String interactionPreview;

	public UUID getCustomerId() { return customerId; }
	public void setCustomerId(UUID customerId) { this.customerId = customerId; }
	public String getCustomerName() { return customerName; }
	public void setCustomerName(String customerName) { this.customerName = customerName; }
	public UUID getOwnerMemberId() { return ownerMemberId; }
	public void setOwnerMemberId(UUID ownerMemberId) { this.ownerMemberId = ownerMemberId; }
	public UUID getInteractionId() { return interactionId; }
	public void setInteractionId(UUID interactionId) { this.interactionId = interactionId; }
	public Instant getInteractionAt() { return interactionAt; }
	public void setInteractionAt(Instant interactionAt) { this.interactionAt = interactionAt; }
	public String getInteractionPreview() { return interactionPreview; }
	public void setInteractionPreview(String interactionPreview) { this.interactionPreview = interactionPreview; }
}
