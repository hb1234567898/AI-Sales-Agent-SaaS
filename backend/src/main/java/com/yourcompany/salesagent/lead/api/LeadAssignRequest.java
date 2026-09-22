package com.yourcompany.salesagent.lead.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record LeadAssignRequest(@NotNull UUID ownerMemberId) {
}
