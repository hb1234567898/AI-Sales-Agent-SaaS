package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CommercialOpportunityRow {
    private UUID id;
    private UUID customerId;
    private String name;
    private String customerName;
    private UUID ownerMemberId;
    private String ownerName;
    private String status;
    private long version;
    private String contactEmail;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { customerId = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    public UUID getOwnerMemberId() { return ownerMemberId; }
    public void setOwnerMemberId(UUID value) { ownerMemberId = value; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String value) { ownerName = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String value) { contactEmail = value; }
}
