package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class ReceiptRow {
    private UUID id;
    private UUID contractId;
    private UUID planId;
    private BigDecimal amount;
    private LocalDate receivedOn;
    private String reference;
    private String note;
    private UUID requestKey;
    private UUID createdBy;
    private String createdByName;
    private Instant createdAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public UUID getContractId() { return contractId; }
    public void setContractId(UUID value) { contractId = value; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID value) { planId = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public void setReceivedOn(LocalDate value) { receivedOn = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public UUID getRequestKey() { return requestKey; }
    public void setRequestKey(UUID value) { requestKey = value; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID value) { createdBy = value; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String value) { createdByName = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
