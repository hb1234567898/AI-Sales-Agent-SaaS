package com.yourcompany.salesagent.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.yourcompany.salesagent.auth.application.AuthProperties;

import tools.jackson.databind.ObjectMapper;

class JwtTokenServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-26T02:00:00Z");
	private static final UUID SESSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final String SIGNING_KEY = Base64.getEncoder().encodeToString(
			"a-test-signing-key-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));

	@Test
	void issuesAndVerifiesAccessAndRefreshTokens() {
		var service = service(NOW);
		var sessionExpiresAt = NOW.plus(Duration.ofHours(12));
		var principal = principal(sessionExpiresAt);

		var access = service.issueAccessToken(principal);
		var refresh = service.issueRefreshToken(SESSION_ID, USER_ID, sessionExpiresAt);

		assertThat(access.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		assertThat(service.parseAccessToken(access.value())).isEqualTo(principal);
		assertThat(service.parseRefreshToken(refresh.value()).sessionId()).isEqualTo(SESSION_ID);
		assertThat(service.parseRefreshToken(refresh.value()).userId()).isEqualTo(USER_ID);
	}

	@Test
	void capsAccessTokenAtTheSessionExpiry() {
		var service = service(NOW);
		var sessionExpiresAt = NOW.plus(Duration.ofMinutes(5));

		var access = service.issueAccessToken(principal(sessionExpiresAt));

		assertThat(access.expiresAt()).isEqualTo(sessionExpiresAt);
	}

	@Test
	void rejectsTamperedAndExpiredTokens() {
		var issuer = service(NOW);
		var access = issuer.issueAccessToken(principal(NOW.plus(Duration.ofHours(12))));
		var parts = access.value().split("\\.");
		var replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
		var tampered = parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);

		assertThatThrownBy(() -> issuer.parseAccessToken(tampered))
				.isInstanceOf(JwtTokenException.class);
		assertThatThrownBy(() -> service(NOW.plus(Duration.ofMinutes(16))).parseAccessToken(access.value()))
				.isInstanceOf(JwtTokenException.class)
				.hasMessage("JWT 已经过期");
	}

	private static JwtTokenService service(Instant now) {
		return new JwtTokenService(
				new AuthProperties(
						Duration.ofMinutes(15),
						Duration.ofHours(12),
						Duration.ofDays(30),
						"ai-sales-agent",
						SIGNING_KEY,
						"",
						"",
						""),
				new ObjectMapper(),
				Clock.fixed(now, ZoneOffset.UTC));
	}

	private static AuthPrincipal principal(Instant expiresAt) {
		return new AuthPrincipal(
				SESSION_ID,
				USER_ID,
				ORGANIZATION_ID,
				MEMBER_ID,
				"chen.mo@demo.local",
				"陈默",
				"演示销售团队",
				"SALES",
				expiresAt);
	}
}
