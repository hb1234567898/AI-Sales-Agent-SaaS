package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class QuoteVersionRow {
    private UUID id;
    private UUID quoteId;
    private int revision;
    private String title;
    private UUID priceBookId;
    private String priceBookName;
    private String customerName;
    private String sellerName;
    private String currency;
    private LocalDate validUntil;
    private String terms;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private UUID fileId;
    private UUID approvalId;
    private Instant createdAt;
    private String createdByName;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public UUID getQuoteId() { return quoteId; }
    public void setQuoteId(UUID value) { quoteId = value; }
    public int getRevision() { return revision; }
    public void setRevision(int value) { revision = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public UUID getPriceBookId() { return priceBookId; }
    public void setPriceBookId(UUID value) { priceBookId = value; }
    public String getPriceBookName() { return priceBookName; }
    public void setPriceBookName(String value) { priceBookName = value; }
    public String getCustomerName() { return customerName; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String value) { sellerName = value; }
    public void setCustomerName(String value) { customerName = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate value) { validUntil = value; }
    public String getTerms() { return terms; }
    public void setTerms(String value) { terms = value; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal value) { subtotal = value; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal value) { tax = value; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal value) { total = value; }
    public UUID getFileId() { return fileId; }
    public void setFileId(UUID value) { fileId = value; }
    public UUID getApprovalId() { return approvalId; }
    public void setApprovalId(UUID value) { approvalId = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String value) { createdByName = value; }
}
