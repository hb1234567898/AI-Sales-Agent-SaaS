package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class PriceBookRow {
    private UUID id;
    private String name;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private boolean active;
    private long version;
    private Instant updatedAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate value) { validFrom = value; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate value) { validUntil = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
