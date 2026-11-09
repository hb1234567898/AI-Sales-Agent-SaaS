package com.yourcompany.salesagent.opportunity.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.yourcompany.salesagent.opportunity.domain.ForecastCategory;
import jakarta.validation.constraints.*;

public record OpportunityUpsertRequest(
		@NotNull UUID customerId,
		@NotBlank @Size(max = 255) String name,
		@NotNull UUID ownerMemberId,
		@NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal amount,
		@NotNull @Min(0) @Max(100) Integer probability,
		LocalDate expectedCloseDate,
		@NotNull ForecastCategory forecastCategory,
		@Size(max = 1000) String nextStep,
		@Min(0) Long version) {}
