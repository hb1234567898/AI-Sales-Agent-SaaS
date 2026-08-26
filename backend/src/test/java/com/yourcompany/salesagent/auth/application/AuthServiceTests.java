package com.yourcompany.salesagent.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.yourcompany.salesagent.auth.domain.AuthSession;
import com.yourcompany.salesagent.auth.infrastructure.AuthAccountRow;
import com.yourcompany.salesagent.auth.infrastructure.AuthMapper;
import com.yourcompany.salesagent.auth.infrastructure.AuthSessionMapper;

class AuthServiceTests {

	private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final Instant NOW = Instant.parse("2026-08-26T02:00:00Z");

	@Test
	void createsServerSideSessionForValidCredentials() {
		var authMapper = mock(AuthMapper.class);
		var sessionMapper = mock(AuthSessionMapper.class);
		var encoder = new BCryptPasswordEncoder(4);
		when(authMapper.selectLoginAccount("chen.mo@demo.local", ORGANIZATION_ID))
				.thenReturn(account(encoder.encode("Demo@123456"), 0));
		var service = service(authMapper, sessionMapper, encoder);

		var result = service.login(
				"chen.mo@demo.local",
				"Demo@123456",
				true,
				"test-browser",
				"127.0.0.1");

		assertThat(result.session().displayName()).isEqualTo("陈默");
		assertThat(result.cookieMaxAge()).isEqualTo(Duration.ofDays(30));
		assertThat(result.rawToken()).hasSizeGreaterThan(40);
		verify(authMapper).recordLoginSuccess(USER_ID, NOW);
		verify(sessionMapper).insert(any(AuthSession.class));
	}

	@Test
	void recordsAFailedAttemptWithoutRevealingWhichCredentialWasWrong() {
		var authMapper = mock(AuthMapper.class);
		var sessionMapper = mock(AuthSessionMapper.class);
		var encoder = new BCryptPasswordEncoder(4);
		when(authMapper.selectLoginAccount("chen.mo@demo.local", ORGANIZATION_ID))
				.thenReturn(account(encoder.encode("Demo@123456"), 1));
		var service = service(authMapper, sessionMapper, encoder);

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
		var service = service(authMapper, sessionMapper, encoder);

		assertThatThrownBy(() -> service.login(
				"chen.mo@demo.local", "wrong-password", false, null, "127.0.0.1"))
				.isInstanceOf(LoginLockedException.class);
		verify(authMapper).recordLoginFailure(USER_ID, 5, NOW.plus(Duration.ofMinutes(15)));
	}

	private static AuthService service(AuthMapper authMapper, AuthSessionMapper sessionMapper, BCryptPasswordEncoder encoder) {
		return new AuthService(
				authMapper,
				sessionMapper,
				encoder,
				Clock.fixed(NOW, ZoneOffset.UTC),
				new AuthProperties("SESSION", false, Duration.ofHours(12), Duration.ofDays(30)),
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
}
