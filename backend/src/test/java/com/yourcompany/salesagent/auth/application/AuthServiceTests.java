package com.yourcompany.salesagent.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.yourcompany.salesagent.auth.domain.AuthSession;
import com.yourcompany.salesagent.auth.infrastructure.AuthAccountRow;
import com.yourcompany.salesagent.auth.infrastructure.AuthMapper;
import com.yourcompany.salesagent.auth.infrastructure.AuthSessionMapper;
import com.yourcompany.salesagent.auth.infrastructure.AuthSessionRow;
import com.yourcompany.salesagent.auth.security.JwtTokenService;

class AuthServiceTests {

	private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID SESSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final Instant NOW = Instant.parse("2026-08-26T02:00:00Z");

	@Test
	void createsRefreshBackedJwtPairForValidCredentials() {
		var authMapper = mock(AuthMapper.class);
		var sessionMapper = mock(AuthSessionMapper.class);
		var tokenService = mock(JwtTokenService.class);
		var encoder = new BCryptPasswordEncoder(4);
		when(authMapper.selectLoginAccount("chen.mo@demo.local", ORGANIZATION_ID))
				.thenReturn(account(encoder.encode("Demo@123456"), 0));
		when(tokenService.issueAccessToken(any()))
				.thenReturn(new JwtTokenService.IssuedToken("access.jwt", NOW.plus(Duration.ofMinutes(15))));
		when(tokenService.issueRefreshToken(any(), eq(USER_ID), eq(NOW.plus(Duration.ofDays(30)))))
				.thenReturn(new JwtTokenService.IssuedToken("refresh.jwt", NOW.plus(Duration.ofDays(30))));
		var service = service(authMapper, sessionMapper, encoder, tokenService);

		var result = service.login(
				"chen.mo@demo.local",
				"Demo@123456",
				true,
				"test-browser",
				"127.0.0.1");

		assertThat(result.principal().displayName()).isEqualTo("陈默");
		assertThat(result.accessToken()).isEqualTo("access.jwt");
		assertThat(result.refreshToken()).isEqualTo("refresh.jwt");
		assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
		verify(authMapper).recordLoginSuccess(USER_ID, NOW);
		var sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
		verify(sessionMapper).insert(sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().getTokenHash()).isEqualTo(AuthService.hashToken("refresh.jwt"));
		assertThat(sessionCaptor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
	}

	@Test
	void rotatesRefreshTokenWhenRefreshingSession() {
		var authMapper = mock(AuthMapper.class);
		var sessionMapper = mock(AuthSessionMapper.class);
		var tokenService = mock(JwtTokenService.class);
		var encoder = new BCryptPasswordEncoder(4);
		var expiresAt = NOW.plus(Duration.ofDays(30));
		var currentHash = AuthService.hashToken("old-refresh.jwt");
		when(tokenService.parseRefreshToken("old-refresh.jwt"))
				.thenReturn(new JwtTokenService.RefreshClaims(SESSION_ID, USER_ID, expiresAt));
		when(authMapper.selectActiveSession(currentHash, NOW)).thenReturn(sessionRow(expiresAt));
		when(tokenService.issueAccessToken(any()))
				.thenReturn(new JwtTokenService.IssuedToken("new-access.jwt", NOW.plus(Duration.ofMinutes(15))));
		when(tokenService.issueRefreshToken(SESSION_ID, USER_ID, expiresAt))
				.thenReturn(new JwtTokenService.IssuedToken("new-refresh.jwt", expiresAt));
		when(authMapper.rotateSessionToken(
				SESSION_ID, currentHash, AuthService.hashToken("new-refresh.jwt"), NOW)).thenReturn(1);
		var service = service(authMapper, sessionMapper, encoder, tokenService);

		var result = service.refresh("old-refresh.jwt");

		assertThat(result.accessToken()).isEqualTo("new-access.jwt");
		assertThat(result.refreshToken()).isEqualTo("new-refresh.jwt");
		verify(authMapper).rotateSessionToken(
				SESSION_ID, currentHash, AuthService.hashToken("new-refresh.jwt"), NOW);
	}

	@Test
	void rejectsARefreshTokenThatLostTheRotationRace() {
		var authMapper = mock(AuthMapper.class);
		var sessionMapper = mock(AuthSessionMapper.class);
		var tokenService = mock(JwtTokenService.class);
		var encoder = new BCryptPasswordEncoder(4);
		var expiresAt = NOW.plus(Duration.ofDays(30));
		var currentHash = AuthService.hashToken("replayed-refresh.jwt");
		when(tokenService.parseRefreshToken("replayed-refresh.jwt"))
				.thenReturn(new JwtTokenService.RefreshClaims(SESSION_ID, USER_ID, expiresAt));
		when(authMapper.selectActiveSession(currentHash, NOW)).thenReturn(sessionRow(expiresAt));
		when(tokenService.issueAccessToken(any()))
				.thenReturn(new JwtTokenService.IssuedToken("new-access.jwt", NOW.plus(Duration.ofMinutes(15))));
		when(tokenService.issueRefreshToken(SESSION_ID, USER_ID, expiresAt))
				.thenReturn(new JwtTokenService.IssuedToken("new-refresh.jwt", expiresAt));
		var service = service(authMapper, sessionMapper, encoder, tokenService);

		assertThatThrownBy(() -> service.refresh("replayed-refresh.jwt"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void recordsAFailedAttemptWithoutRevealingWhichCredentialWasWrong() {
		var authMapper = mock(AuthMapper.class);
		var sessionMapper = mock(AuthSessionMapper.class);
		var encoder = new BCryptPasswordEncoder(4);
		when(authMapper.selectLoginAccount("chen.mo@demo.local", ORGANIZATION_ID))
				.thenReturn(account(encoder.encode("Demo@123456"), 1));
		var service = service(authMapper, sessionMapper, encoder, mock(JwtTokenService.class));

		assertThatThrownBy(() -> service.login(
				"chen.mo@demo.local", "wrong-password", false, null, "127.0.0.1"))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("邮箱或密码不正确");
		verify(authMapper).recordLoginFailure(USER_ID, 2, null);
	}

	@Test
	void locksTheAccountAfterFiveFailedAttempts() {
		var authMapper = mock(AuthMapper.class);
		var sessionMapper = mock(AuthSessionMapper.class);
		var encoder = new BCryptPasswordEncoder(4);
		when(authMapper.selectLoginAccount("chen.mo@demo.local", ORGANIZATION_ID))
				.thenReturn(account(encoder.encode("Demo@123456"), 4));
		var service = service(authMapper, sessionMapper, encoder, mock(JwtTokenService.class));

		assertThatThrownBy(() -> service.login(
				"chen.mo@demo.local", "wrong-password", false, null, "127.0.0.1"))
				.isInstanceOf(LoginLockedException.class);
		verify(authMapper).recordLoginFailure(USER_ID, 5, NOW.plus(Duration.ofMinutes(15)));
	}

	private static AuthService service(
			AuthMapper authMapper,
			AuthSessionMapper sessionMapper,
			BCryptPasswordEncoder encoder,
			JwtTokenService tokenService) {
		return new AuthService(
				authMapper,
				sessionMapper,
				encoder,
				tokenService,
				Clock.fixed(NOW, ZoneOffset.UTC),
				new AuthProperties(
						Duration.ofMinutes(15),
						Duration.ofHours(12),
						Duration.ofDays(30),
						"ai-sales-agent",
						"unused-in-mocked-service"),
				ORGANIZATION_ID);
	}

	private static AuthAccountRow account(String passwordHash, int failedAttempts) {
		return new AuthAccountRow(
				USER_ID,
				"chen.mo@demo.local",
				"陈默",
				passwordHash,
				failedAttempts,
				null,
				MEMBER_ID,
				ORGANIZATION_ID,
				"演示销售团队",
				"SALES");
	}

	private static AuthSessionRow sessionRow(Instant expiresAt) {
		return new AuthSessionRow(
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
