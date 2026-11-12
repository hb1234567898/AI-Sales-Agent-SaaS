package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class PaymentPlanRow {
    private UUID id;
    private UUID contractId;
    private String contractNumber;
    private String customerName;
    private int position;
    private String name;
    private BigDecimal amount;
    private BigDecimal paid;
    private LocalDate dueOn;
    private String status;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public UUID getContractId() { return contractId; }
    public void setContractId(UUID value) { contractId = value; }
    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String value) { contractNumber = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    public int getPosition() { return position; }
    public void setPosition(int value) { position = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public BigDecimal getPaid() { return paid; }
    public void setPaid(BigDecimal value) { paid = value; }
    public LocalDate getDueOn() { return dueOn; }
    public void setDueOn(LocalDate value) { dueOn = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
}
