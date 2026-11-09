package com.yourcompany.salesagent.opportunity.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.yourcompany.salesagent.opportunity.domain.ForecastCategory;
import com.yourcompany.salesagent.opportunity.domain.OpportunityStage;

public class OpportunityRow {
	private UUID id;
	private UUID customerId;
	private String company;
	private String name;
	private UUID ownerMemberId;
	private String ownerName;
	private OpportunityStage stage;
	private String status;
	private BigDecimal amount;
	private Integer probability;
	private LocalDate expectedCloseDate;
	private ForecastCategory forecastCategory;
	private String nextStep;
	private String closeReason;
	private Instant stageChangedAt;
	private Instant updatedAt;
	private long version;
	public UUID getId() { return id; }
	public void setId(UUID value) { id = value; }
	public UUID getCustomerId() { return customerId; }
	public void setCustomerId(UUID value) { customerId = value; }
	public String getCompany() { return company; }
	public void setCompany(String value) { company = value; }
	public String getName() { return name; }
	public void setName(String value) { name = value; }
	public UUID getOwnerMemberId() { return ownerMemberId; }
	public void setOwnerMemberId(UUID value) { ownerMemberId = value; }
	public String getOwnerName() { return ownerName; }
	public void setOwnerName(String value) { ownerName = value; }
	public OpportunityStage getStage() { return stage; }
	public void setStage(OpportunityStage value) { stage = value; }
	public String getStatus() { return status; }
	public void setStatus(String value) { status = value; }
	public BigDecimal getAmount() { return amount; }
	public void setAmount(BigDecimal value) { amount = value; }
	public Integer getProbability() { return probability; }
	public void setProbability(Integer value) { probability = value; }
	public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
	public void setExpectedCloseDate(LocalDate value) { expectedCloseDate = value; }
	public ForecastCategory getForecastCategory() { return forecastCategory; }
	public void setForecastCategory(ForecastCategory value) { forecastCategory = value; }
	public String getNextStep() { return nextStep; }
	public void setNextStep(String value) { nextStep = value; }
	public String getCloseReason() { return closeReason; }
	public void setCloseReason(String value) { closeReason = value; }
	public Instant getStageChangedAt() { return stageChangedAt; }
	public void setStageChangedAt(Instant value) { stageChangedAt = value; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant value) { updatedAt = value; }
	public long getVersion() { return version; }
	public void setVersion(long value) { version = value; }
}
