package com.yourcompany.salesagent.lead.api;

import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.lead.domain.LeadStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LeadUpsertRequest(
		@NotBlank @Size(max = 255) String company,
		@Size(max = 120) String industry,
		@NotBlank @Size(max = 220) String contactName,
		@Email @Size(max = 320) String contactEmail,
		@Size(max = 50) String contactPhone,
		@Size(max = 120) String contactTitle,
		@NotNull CustomerSource source,
		@NotNull LeadStatus status,
		UUID ownerMemberId,
		@Min(0) @Max(100) Integer score,
		@Size(max = 500) String nextAction,
		Instant nextFollowUpAt) {
}
