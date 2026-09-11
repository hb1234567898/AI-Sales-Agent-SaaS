package com.yourcompany.salesagent.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogCommand(
		UUID organizationId,
		UUID actorMemberId,
		String actorIdentifier,
		String action,
		String targetType,
		String targetId,
		String result,
		String ipAddress,
		String userAgent,
		String requestId,
		Map<String, Object> metadata,
		Instant occurredAt) {
}
