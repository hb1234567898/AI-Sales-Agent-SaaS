package com.yourcompany.salesagent.admin.infrastructure;

import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.admin.domain.MemberRole;
import com.yourcompany.salesagent.admin.domain.MemberStatus;

public record AdminMemberRow(
		UUID id,
		UUID userId,
		String email,
		String displayName,
		MemberRole role,
		MemberStatus status,
		Instant joinedAt,
		Instant lastLoginAt,
		Instant createdAt) {
}
