package com.yourcompany.salesagent.opportunity.api;

import com.yourcompany.salesagent.opportunity.domain.OpportunityStage;
import jakarta.validation.constraints.*;

public record OpportunityStageRequest(@NotNull OpportunityStage stage,
		@NotNull @Min(0) Long version, @Size(max = 1000) String reason) {}
