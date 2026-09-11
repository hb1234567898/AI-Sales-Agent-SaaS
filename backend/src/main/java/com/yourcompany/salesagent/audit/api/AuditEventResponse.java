package com.yourcompany.salesagent.audit.api;

import java.time.Instant;
import java.util.Map;

import com.yourcompany.salesagent.audit.domain.AuditEvent;

public record AuditEventResponse(
		Long id,
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

	public static AuditEventResponse from(AuditEvent event) {
		return new AuditEventResponse(
				event.getId(),
				event.getActorIdentifier(),
				event.getAction(),
				event.getTargetType(),
				event.getTargetId(),
				event.getResult(),
				event.getIpAddress(),
				event.getUserAgent(),
				event.getRequestId(),
				event.getMetadata(),
				event.getOccurredAt());
	}
}
