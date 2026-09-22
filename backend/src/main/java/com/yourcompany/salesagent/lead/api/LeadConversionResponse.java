package com.yourcompany.salesagent.lead.api;

import java.util.UUID;

public record LeadConversionResponse(UUID leadId, UUID customerId, UUID opportunityId) {
}
