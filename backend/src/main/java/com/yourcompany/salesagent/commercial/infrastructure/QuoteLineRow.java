package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class QuoteLineRow {
    private int position;
    private UUID productId;
    private String sku;
    private String productName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPercent;
    private BigDecimal taxRate;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    public int getPosition() { return position; }
    public void setPosition(int value) { position = value; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID value) { productId = value; }
    public String getSku() { return sku; }
    public void setSku(String value) { sku = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { productName = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal value) { discountPercent = value; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal value) { taxRate = value; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal value) { subtotal = value; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal value) { tax = value; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal value) { total = value; }
}
