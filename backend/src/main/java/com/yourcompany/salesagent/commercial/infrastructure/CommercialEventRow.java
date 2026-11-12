package com.yourcompany.salesagent.commercial.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CommercialEventRow {
    private UUID id;
    private String actorName;
    private String summary;
    private Instant createdAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getActorName() { return actorName; }
    public void setActorName(String value) { actorName = value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { summary = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
