package com.yourcompany.salesagent.lead.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeadConvertRequest(
		@NotBlank @Size(max = 255) String opportunityName,
		@DecimalMin("0") BigDecimal amount,
		LocalDate expectedCloseDate) {
}
