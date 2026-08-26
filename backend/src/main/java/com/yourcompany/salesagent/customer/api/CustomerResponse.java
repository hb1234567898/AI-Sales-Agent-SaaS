package com.yourcompany.salesagent.customer.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.customer.domain.CustomerStage;
import com.yourcompany.salesagent.customer.domain.CustomerStatus;

public record CustomerResponse(
		UUID id,
		String name,
		String website,
		String industry,
		String employeeRange,
		CustomerStage stage,
		CustomerStatus status,
		CustomerSource source,
		UUID ownerMemberId,
		String ownerName,
		Integer score,
		BigDecimal estimatedValue,
		String nextAction,
		Instant lastInteractionAt,
		Instant nextFollowUpAt,
		PrimaryContactResponse primaryContact,
		Instant createdAt,
		Instant updatedAt,
		long version) {

	public record PrimaryContactResponse(String name, String email, String phone) {
	}
}
