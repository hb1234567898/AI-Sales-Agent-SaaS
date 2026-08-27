package com.yourcompany.salesagent.auth.application;

import java.time.Instant;

import com.yourcompany.salesagent.auth.security.AuthPrincipal;

public record AuthTokenPair(
		String accessToken,
		Instant accessTokenExpiresAt,
		String refreshToken,
		Instant refreshTokenExpiresAt,
		AuthPrincipal principal) {
}
