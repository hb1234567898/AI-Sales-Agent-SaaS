package com.yourcompany.salesagent.customer.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.customer.domain.CustomerStage;
import com.yourcompany.salesagent.customer.domain.CustomerStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerUpsertRequest(
		@NotBlank @Size(max = 255) String name,
		@Size(max = 500) String website,
		@Size(max = 120) String industry,
		@Size(max = 40) String employeeRange,
		CustomerStage stage,
		CustomerStatus status,
		CustomerSource source,
		UUID ownerMemberId,
		@Min(0) @Max(100) Integer score,
		@DecimalMin("0.0") BigDecimal estimatedValue,
		@Size(max = 500) String nextAction,
		Instant nextFollowUpAt,
		@Size(max = 220) String primaryContactName,
		@Email @Size(max = 320) String primaryContactEmail,
		@Size(max = 50) String primaryContactPhone) {
}
