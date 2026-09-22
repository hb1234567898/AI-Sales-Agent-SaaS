package com.yourcompany.salesagent.lead.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record LeadImportRequest(@NotEmpty @Size(max = 500) List<@Valid LeadUpsertRequest> leads) {
}
