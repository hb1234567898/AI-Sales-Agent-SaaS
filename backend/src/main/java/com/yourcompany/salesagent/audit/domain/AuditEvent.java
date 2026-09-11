package com.yourcompany.salesagent.audit.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yourcompany.salesagent.shared.persistence.JsonbMapTypeHandler;

@TableName(value = "audit_event", autoResultMap = true)
public class AuditEvent {

	@TableId(type = IdType.AUTO)
	private Long id;

	@TableField("organization_id")
	private UUID organizationId;

	@TableField("actor_type")
	private String actorType;

	@TableField("actor_member_id")
	private UUID actorMemberId;

	@TableField("actor_identifier")
	private String actorIdentifier;

	private String action;

	@TableField("target_type")
	private String targetType;

	@TableField("target_id")
	private String targetId;

	private String result;

	@TableField("ip_address")
	private String ipAddress;

	@TableField("user_agent")
	private String userAgent;

	@TableField("request_id")
	private String requestId;

	@TableField(typeHandler = JsonbMapTypeHandler.class)
	private Map<String, Object> metadata = new HashMap<>();

	@TableField("occurred_at")
	private Instant occurredAt;

	protected AuditEvent() {
	}

	public static AuditEvent create(
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
		var event = new AuditEvent();
		event.organizationId = organizationId;
		event.actorType = "USER";
		event.actorMemberId = actorMemberId;
		event.actorIdentifier = actorIdentifier;
		event.action = action;
		event.targetType = targetType;
		event.targetId = targetId;
		event.result = result;
		event.ipAddress = ipAddress;
		event.userAgent = userAgent;
		event.requestId = requestId;
		event.metadata = new HashMap<>(metadata);
		event.occurredAt = occurredAt;
		return event;
	}

	public Long getId() { return id; }
	public UUID getOrganizationId() { return organizationId; }
	public String getActorType() { return actorType; }
	public UUID getActorMemberId() { return actorMemberId; }
	public String getActorIdentifier() { return actorIdentifier; }
	public String getAction() { return action; }
	public String getTargetType() { return targetType; }
	public String getTargetId() { return targetId; }
	public String getResult() { return result; }
	public String getIpAddress() { return ipAddress; }
	public String getUserAgent() { return userAgent; }
	public String getRequestId() { return requestId; }
	public Map<String, Object> getMetadata() { return metadata; }
	public Instant getOccurredAt() { return occurredAt; }
}
