package com.yourcompany.salesagent.admin.api;

import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.admin.domain.MemberRole;
import com.yourcompany.salesagent.admin.domain.MemberStatus;
import com.yourcompany.salesagent.admin.infrastructure.AdminMemberRow;

public record AdminMemberResponse(
		UUID id,
		UUID userId,
		String email,
		String displayName,
		MemberRole role,
		MemberStatus status,
		Instant joinedAt,
		Instant lastLoginAt,
		Instant createdAt) {

	public static AdminMemberResponse from(AdminMemberRow row) {
		return new AdminMemberResponse(
				row.id(), row.userId(), row.email(), row.displayName(), row.role(), row.status(),
				row.joinedAt(), row.lastLoginAt(), row.createdAt());
	}
}
