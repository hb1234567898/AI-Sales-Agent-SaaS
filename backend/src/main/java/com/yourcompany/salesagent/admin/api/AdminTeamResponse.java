package com.yourcompany.salesagent.admin.api;

import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.admin.infrastructure.AdminTeamRow;

public record AdminTeamResponse(
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

	public static AdminTeamResponse from(AdminTeamRow row) {
		return new AdminTeamResponse(
				row.id(), row.slug(), row.name(), row.timezone(), row.locale(), row.planCode(), row.status(),
				row.totalMembers(), row.activeMembers(), row.adminMembers(), row.createdAt(), row.updatedAt());
	}
}
