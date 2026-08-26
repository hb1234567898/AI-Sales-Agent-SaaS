package com.yourcompany.salesagent.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourcompany.salesagent.auth.api.AuthSessionResponse;
import com.yourcompany.salesagent.auth.domain.AuthSession;
import com.yourcompany.salesagent.auth.infrastructure.AuthAccountRow;
import com.yourcompany.salesagent.auth.infrastructure.AuthMapper;
import com.yourcompany.salesagent.auth.infrastructure.AuthSessionMapper;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;

@Service
public class AuthService {

	private static final int MAX_FAILED_ATTEMPTS = 5;
	private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final AuthMapper authMapper;
	private final AuthSessionMapper sessionMapper;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final AuthProperties properties;
	private final UUID organizationId;

	public AuthService(
			AuthMapper authMapper,
			AuthSessionMapper sessionMapper,
			PasswordEncoder passwordEncoder,
			Clock clock,
			AuthProperties properties,
			@Value("${app.demo.organization-id}") UUID organizationId) {
		this.authMapper = authMapper;
		this.sessionMapper = sessionMapper;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.properties = properties;
		this.organizationId = organizationId;
	}

	@Transactional(noRollbackFor = { InvalidCredentialsException.class, LoginLockedException.class })
	public LoginResult login(String email, String password, boolean rememberMe, String userAgent, String ipAddress) {
		var now = clock.instant();
		var account = authMapper.selectLoginAccount(email.strip(), organizationId);
		if (account == null) {
			passwordEncoder.matches(password, "$2a$10$7EqJtq98hPqEX7fNZaFWoO5uUEj18ylZjBqq9ZwNl7GypEY7W9ObK");
			throw new InvalidCredentialsException();
		}

		if (account.lockedUntil() != null && account.lockedUntil().isAfter(now)) {
			throw new LoginLockedException(account.lockedUntil());
		}

		if (!passwordEncoder.matches(password, account.passwordHash())) {
			recordFailure(account, now);
		}

		authMapper.recordLoginSuccess(account.userId(), now);
		var rawToken = generateToken();
		var duration = rememberMe ? properties.rememberDuration() : properties.sessionDuration();
		var expiresAt = now.plus(duration);
		var session = AuthSession.create(
				account.userId(),
				account.organizationId(),
				account.memberId(),
				hashToken(rawToken),
				expiresAt,
				userAgent,
				ipAddress,
				now);
		sessionMapper.insert(session);

		var principal = toPrincipal(account, session.getId(), expiresAt);
		return new LoginResult(AuthSessionResponse.from(principal), rawToken, duration);
	}

	@Transactional
	public Optional<AuthPrincipal> resolveSession(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		var now = clock.instant();
		var row = authMapper.selectActiveSession(hashToken(rawToken), now);
		if (row == null) {
			return Optional.empty();
		}
		authMapper.touchSession(row.sessionId(), now.minus(Duration.ofMinutes(5)), now);
		return Optional.of(AuthPrincipal.from(row));
	}

	@Transactional
	public void logout(UUID sessionId) {
		authMapper.revokeSession(sessionId, clock.instant());
	}

	private void recordFailure(AuthAccountRow account, Instant now) {
		var attempts = account.lockedUntil() != null && !account.lockedUntil().isAfter(now)
				? 1
				: account.failedAttempts() + 1;
		var lockedUntil = attempts >= MAX_FAILED_ATTEMPTS ? now.plus(LOCK_DURATION) : null;
		authMapper.recordLoginFailure(account.userId(), attempts, lockedUntil);
		if (lockedUntil != null) {
			throw new LoginLockedException(lockedUntil);
		}
		throw new InvalidCredentialsException();
	}

	private static AuthPrincipal toPrincipal(AuthAccountRow account, UUID sessionId, Instant expiresAt) {
		return new AuthPrincipal(
				sessionId,
				account.userId(),
				account.organizationId(),
				account.memberId(),
				account.email(),
				account.displayName(),
				account.organizationName(),
				account.role(),
				expiresAt);
	}

	private static String generateToken() {
		var bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hashToken(String token) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", exception);
		}
	}

	public record LoginResult(AuthSessionResponse session, String rawToken, Duration cookieMaxAge) {
	}
}
