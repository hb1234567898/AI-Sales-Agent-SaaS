package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class ContractRow {
    private UUID id;
    private String number;
    private UUID quoteId;
    private UUID quoteVersionId;
    private String quoteNumber;
    private int quoteRevision;
    private UUID opportunityId;
    private UUID customerId;
    private String customerName;
    private UUID ownerMemberId;
    private String ownerName;
    private String name;
    private BigDecimal total;
    private BigDecimal paid;
    private String currency;
    private String status;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private String terms;
    private LocalDate signedOn;
    private UUID signedFileId;
    private String signedFilename;
    private long version;
    private Instant updatedAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getNumber() { return number; }
    public void setNumber(String value) { number = value; }
    public UUID getQuoteId() { return quoteId; }
    public void setQuoteId(UUID value) { quoteId = value; }
    public UUID getQuoteVersionId() { return quoteVersionId; }
    public void setQuoteVersionId(UUID value) { quoteVersionId = value; }
    public String getQuoteNumber() { return quoteNumber; }
    public void setQuoteNumber(String value) { quoteNumber = value; }
    public int getQuoteRevision() { return quoteRevision; }
    public void setQuoteRevision(int value) { quoteRevision = value; }
    public UUID getOpportunityId() { return opportunityId; }
    public void setOpportunityId(UUID value) { opportunityId = value; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { customerId = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    public UUID getOwnerMemberId() { return ownerMemberId; }
    public void setOwnerMemberId(UUID value) { ownerMemberId = value; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String value) { ownerName = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal value) { total = value; }
    public BigDecimal getPaid() { return paid; }
    public void setPaid(BigDecimal value) { paid = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate value) { startsOn = value; }
    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate value) { endsOn = value; }
    public String getTerms() { return terms; }
    public void setTerms(String value) { terms = value; }
    public LocalDate getSignedOn() { return signedOn; }
    public void setSignedOn(LocalDate value) { signedOn = value; }
    public UUID getSignedFileId() { return signedFileId; }
    public void setSignedFileId(UUID value) { signedFileId = value; }
    public String getSignedFilename() { return signedFilename; }
    public void setSignedFilename(String value) { signedFilename = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
