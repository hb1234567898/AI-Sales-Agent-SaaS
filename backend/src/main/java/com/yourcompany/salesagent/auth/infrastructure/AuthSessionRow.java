package com.yourcompany.salesagent.auth.infrastructure;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionRow(
		UUID sessionId,
		UUID userId,
		UUID organizationId,
		UUID memberId,
		String email,
		String displayName,
		String organizationName,
		String role,
		Instant expiresAt) {
}
