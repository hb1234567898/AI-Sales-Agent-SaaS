package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class ProductRow {
    private UUID id;
    private String sku;
    private String name;
    private String unit;
    private BigDecimal listPrice;
    private BigDecimal taxRate;
    private boolean active;
    private String description;
    private long version;
    private Instant updatedAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getSku() { return sku; }
    public void setSku(String value) { sku = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public BigDecimal getListPrice() { return listPrice; }
    public void setListPrice(BigDecimal value) { listPrice = value; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal value) { taxRate = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
