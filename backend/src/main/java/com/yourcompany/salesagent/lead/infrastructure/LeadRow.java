package com.yourcompany.salesagent.lead.infrastructure;

import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.lead.domain.LeadStatus;

public class LeadRow {
	private UUID id;
	private String company;
	private String industry;
	private String contactName;
	private String contactEmail;
	private String contactPhone;
	private String contactTitle;
	private CustomerSource source;
	private LeadStatus status;
	private UUID ownerMemberId;
	private String ownerName;
	private Integer score;
	private String nextAction;
	private Instant nextFollowUpAt;
	private Instant lastActivityAt;
	private Instant createdAt;
	private Instant updatedAt;

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public String getCompany() { return company; }
	public void setCompany(String company) { this.company = company; }
	public String getIndustry() { return industry; }
	public void setIndustry(String industry) { this.industry = industry; }
	public String getContactName() { return contactName; }
	public void setContactName(String contactName) { this.contactName = contactName; }
	public String getContactEmail() { return contactEmail; }
	public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
	public String getContactPhone() { return contactPhone; }
	public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
	public String getContactTitle() { return contactTitle; }
	public void setContactTitle(String contactTitle) { this.contactTitle = contactTitle; }
	public CustomerSource getSource() { return source; }
	public void setSource(CustomerSource source) { this.source = source; }
	public LeadStatus getStatus() { return status; }
	public void setStatus(LeadStatus status) { this.status = status; }
	public UUID getOwnerMemberId() { return ownerMemberId; }
	public void setOwnerMemberId(UUID ownerMemberId) { this.ownerMemberId = ownerMemberId; }
	public String getOwnerName() { return ownerName; }
	public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
	public Integer getScore() { return score; }
	public void setScore(Integer score) { this.score = score; }
	public String getNextAction() { return nextAction; }
	public void setNextAction(String nextAction) { this.nextAction = nextAction; }
	public Instant getNextFollowUpAt() { return nextFollowUpAt; }
	public void setNextFollowUpAt(Instant nextFollowUpAt) { this.nextFollowUpAt = nextFollowUpAt; }
	public Instant getLastActivityAt() { return lastActivityAt; }
	public void setLastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
