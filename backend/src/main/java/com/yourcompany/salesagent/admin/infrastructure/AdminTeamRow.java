package com.yourcompany.salesagent.admin.infrastructure;

import java.time.Instant;
import java.util.UUID;

public record AdminTeamRow(
		UUID id,
		String slug,
		String name,
		String timezone,
		String locale,
		String planCode,
		String status,
		long totalMembers,
		long activeMembers,
		long adminMembers,
		Instant createdAt,
		Instant updatedAt) {
}
