package com.yourcompany.salesagent.auth.security;

import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.auth.infrastructure.AuthSessionRow;

public record AuthPrincipal(
		UUID sessionId,
		UUID userId,
		UUID organizationId,
		UUID memberId,
		String email,
		String displayName,
		String organizationName,
		String role,
		Instant expiresAt) {

	public static AuthPrincipal from(AuthSessionRow session) {
		return new AuthPrincipal(
				session.sessionId(),
				session.userId(),
				session.organizationId(),
				session.memberId(),
				session.email(),
				session.displayName(),
				session.organizationName(),
				session.role(),
				session.expiresAt());
	}
}
