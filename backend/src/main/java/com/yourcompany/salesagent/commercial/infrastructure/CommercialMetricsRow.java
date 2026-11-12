package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CommercialMetricsRow {
    private long draftQuotes;
    private long awaitingCustomer;
    private BigDecimal signedAmount;
    private BigDecimal receivable;
    private BigDecimal overdueAmount;
    public long getDraftQuotes() { return draftQuotes; }
    public void setDraftQuotes(long value) { draftQuotes = value; }
    public long getAwaitingCustomer() { return awaitingCustomer; }
    public void setAwaitingCustomer(long value) { awaitingCustomer = value; }
    public BigDecimal getSignedAmount() { return signedAmount; }
    public void setSignedAmount(BigDecimal value) { signedAmount = value; }
    public BigDecimal getReceivable() { return receivable; }
    public void setReceivable(BigDecimal value) { receivable = value; }
    public BigDecimal getOverdueAmount() { return overdueAmount; }
    public void setOverdueAmount(BigDecimal value) { overdueAmount = value; }
}
