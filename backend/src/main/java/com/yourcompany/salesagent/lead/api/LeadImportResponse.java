package com.yourcompany.salesagent.lead.api;

import java.util.List;

public record LeadImportResponse(int total, int created, int skipped, List<String> errors) {
}
