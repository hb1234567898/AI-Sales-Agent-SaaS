package com.yourcompany.salesagent.interaction.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yourcompany.salesagent.shared.persistence.JsonbMapTypeHandler;
import com.yourcompany.salesagent.shared.persistence.JsonbStringListTypeHandler;

@TableName(value = "interaction", autoResultMap = true)
public class Interaction {

	@TableId(type = IdType.INPUT)
	private UUID id;

	@TableField("organization_id")
	private UUID organizationId;

	@TableField("customer_id")
	private UUID customerId;

	private InteractionType type;

	private InteractionDirection direction;

	@TableField("occurred_at")
	private Instant occurredAt;

	private String subject;

	@TableField("body_text")
	private String bodyText;

	@TableField("body_preview")
	private String bodyPreview;

	@TableField(typeHandler = JsonbStringListTypeHandler.class)
	private List<String> participants = new ArrayList<>();

	private String source;

	@TableField(value = "source_payload", typeHandler = JsonbMapTypeHandler.class)
	private Map<String, Object> sourcePayload = new HashMap<>();

	@TableField(typeHandler = JsonbMapTypeHandler.class)
	private Map<String, Object> metadata = new HashMap<>();

	@TableField("created_at")
	private Instant createdAt;

	protected Interaction() {
	}

	public static Interaction create(
			UUID organizationId,
			UUID customerId,
			InteractionType type,
			InteractionDirection direction,
			Instant occurredAt,
			String subject,
			String bodyText,
			List<String> participants,
			String source,
			Map<String, Object> metadata,
			Instant now) {
		var interaction = new Interaction();
		interaction.id = UUID.randomUUID();
		interaction.organizationId = organizationId;
		interaction.customerId = customerId;
		interaction.type = type;
		interaction.direction = direction;
		interaction.occurredAt = occurredAt;
		interaction.subject = subject;
		interaction.bodyText = bodyText;
		interaction.bodyPreview = preview(bodyText);
		interaction.participants = new ArrayList<>(participants);
		interaction.source = source;
		interaction.metadata = new HashMap<>(metadata);
		interaction.createdAt = now;
		return interaction;
	}

	private static String preview(String bodyText) {
		var normalized = bodyText.replaceAll("\\s+", " ").strip();
		return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "…";
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public InteractionType getType() {
		return type;
	}

	public InteractionDirection getDirection() {
		return direction;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getSubject() {
		return subject;
	}

	public String getBodyText() {
		return bodyText;
	}

	public String getBodyPreview() {
		return bodyPreview;
	}

	public List<String> getParticipants() {
		return participants;
	}

	public String getSource() {
		return source;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
