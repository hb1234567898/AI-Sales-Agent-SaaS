package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class PriceEntryRow {
    private UUID productId;
    private String sku;
    private String productName;
    private BigDecimal unitPrice;
    public UUID getProductId() { return productId; }
    public void setProductId(UUID value) { productId = value; }
    public String getSku() { return sku; }
    public void setSku(String value) { sku = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { productName = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
}
