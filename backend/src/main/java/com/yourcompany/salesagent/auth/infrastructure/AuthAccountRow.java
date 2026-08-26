package com.yourcompany.salesagent.auth.infrastructure;

import java.time.Instant;
import java.util.UUID;

public record AuthAccountRow(
		UUID userId,
		String email,
		String displayName,
		String passwordHash,
		int failedAttempts,
		Instant lockedUntil,
		UUID memberId,
		UUID organizationId,
		String organizationName,
		String role) {
}
