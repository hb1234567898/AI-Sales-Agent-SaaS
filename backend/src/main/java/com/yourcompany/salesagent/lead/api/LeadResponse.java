package com.yourcompany.salesagent.lead.api;

import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.lead.domain.LeadStatus;
import com.yourcompany.salesagent.lead.infrastructure.LeadRow;

public record LeadResponse(
		UUID id,
		String company,
		String industry,
		String contactName,
		String contactEmail,
		String contactPhone,
		String contactTitle,
		CustomerSource source,
		LeadStatus status,
		UUID ownerMemberId,
		String ownerName,
		Integer score,
		String nextAction,
		Instant nextFollowUpAt,
		Instant lastActivityAt,
		Instant createdAt,
		Instant updatedAt) {

	public static LeadResponse from(LeadRow row) {
		return new LeadResponse(row.getId(), row.getCompany(), row.getIndustry(), row.getContactName(),
				row.getContactEmail(), row.getContactPhone(), row.getContactTitle(), row.getSource(), row.getStatus(),
				row.getOwnerMemberId(), row.getOwnerName(), row.getScore(), row.getNextAction(),
				row.getNextFollowUpAt(), row.getLastActivityAt(), row.getCreatedAt(), row.getUpdatedAt());
	}
}
