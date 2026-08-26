package com.yourcompany.salesagent.auth.api;

import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.auth.security.AuthPrincipal;

public record AuthSessionResponse(
		UUID userId,
		UUID memberId,
		UUID organizationId,
		String email,
		String displayName,
		String organizationName,
		String role,
		Instant expiresAt) {

	public static AuthSessionResponse from(AuthPrincipal principal) {
		return new AuthSessionResponse(
				principal.userId(),
				principal.memberId(),
				principal.organizationId(),
				principal.email(),
				principal.displayName(),
				principal.organizationName(),
				principal.role(),
				principal.expiresAt());
	}
}
