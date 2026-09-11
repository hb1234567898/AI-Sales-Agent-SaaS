package com.yourcompany.salesagent.auth.api;

import java.time.Instant;

import com.yourcompany.salesagent.auth.application.AuthTokenPair;

public record AuthTokenResponse(
		String tokenType,
		String accessToken,
		Instant accessTokenExpiresAt,
		String refreshToken,
		Instant refreshTokenExpiresAt,
		AuthSessionResponse session) {

	public static AuthTokenResponse from(AuthTokenPair pair) {
		return new AuthTokenResponse(
				"Bearer",
				pair.accessToken(),
				pair.accessTokenExpiresAt(),
				pair.refreshToken(),
				pair.refreshTokenExpiresAt(),
				AuthSessionResponse.from(pair.principal()));
	}
}
