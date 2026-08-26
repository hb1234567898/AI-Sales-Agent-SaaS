package com.yourcompany.salesagent.auth.infrastructure;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface AuthMapper {

	AuthAccountRow selectLoginAccount(@Param("email") String email, @Param("organizationId") UUID organizationId);

	AuthSessionRow selectActiveSession(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

	int recordLoginFailure(
			@Param("userId") UUID userId,
			@Param("failedAttempts") int failedAttempts,
			@Param("lockedUntil") Instant lockedUntil);

	int recordLoginSuccess(@Param("userId") UUID userId, @Param("now") Instant now);

	int touchSession(
			@Param("sessionId") UUID sessionId,
			@Param("seenBefore") Instant seenBefore,
			@Param("now") Instant now);

	int revokeSession(@Param("sessionId") UUID sessionId, @Param("now") Instant now);
}
