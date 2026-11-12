package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class QuoteRow {
    private UUID id;
    private String number;
    private UUID opportunityId;
    private String opportunityName;
    private UUID customerId;
    private String customerName;
    private UUID ownerMemberId;
    private String ownerName;
    private String contactEmail;
    private String status;
    private int revision;
    private long version;
    private String title;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private LocalDate validUntil;
    private Instant updatedAt;
    private UUID approvalId;
    private String approvalStatus;
    private String actionStatus;
    private String deliveryStatus;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getNumber() { return number; }
    public void setNumber(String value) { number = value; }
    public UUID getOpportunityId() { return opportunityId; }
    public void setOpportunityId(UUID value) { opportunityId = value; }
    public String getOpportunityName() { return opportunityName; }
    public void setOpportunityName(String value) { opportunityName = value; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { customerId = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    public UUID getOwnerMemberId() { return ownerMemberId; }
    public void setOwnerMemberId(UUID value) { ownerMemberId = value; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String value) { ownerName = value; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String value) { contactEmail = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public int getRevision() { return revision; }
    public void setRevision(int value) { revision = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal value) { subtotal = value; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal value) { tax = value; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal value) { total = value; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate value) { validUntil = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
    public UUID getApprovalId() { return approvalId; }
    public void setApprovalId(UUID value) { approvalId = value; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String value) { approvalStatus = value; }
    public String getActionStatus() { return actionStatus; }
    public void setActionStatus(String value) { actionStatus = value; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String value) { deliveryStatus = value; }
}
