package com.yourcompany.salesagent.auth.domain;

import java.time.Instant;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("auth_session")
public class AuthSession {

	@TableId(type = IdType.INPUT)
	private UUID id;

	@TableField("user_id")
	private UUID userId;

	@TableField("organization_id")
	private UUID organizationId;

	@TableField("member_id")
	private UUID memberId;

	@TableField("token_hash")
	private String tokenHash;

	@TableField("expires_at")
	private Instant expiresAt;

	@TableField("last_seen_at")
	private Instant lastSeenAt;

	@TableField("user_agent")
	private String userAgent;

	@TableField("ip_address")
	private String ipAddress;

	@TableField("created_at")
	private Instant createdAt;

	@TableField("revoked_at")
	private Instant revokedAt;

	protected AuthSession() {
	}

	public static AuthSession create(
			UUID sessionId,
			UUID userId,
			UUID organizationId,
			UUID memberId,
			String tokenHash,
			Instant expiresAt,
			String userAgent,
			String ipAddress,
			Instant now) {
		var session = new AuthSession();
		session.id = sessionId;
		session.userId = userId;
		session.organizationId = organizationId;
		session.memberId = memberId;
		session.tokenHash = tokenHash;
		session.expiresAt = expiresAt;
		session.lastSeenAt = now;
		session.userAgent = truncate(userAgent, 500);
		session.ipAddress = truncate(ipAddress, 64);
		session.createdAt = now;
		return session;
	}

	private static String truncate(String value, int maximumLength) {
		if (value == null || value.length() <= maximumLength) {
			return value;
		}
		return value.substring(0, maximumLength);
	}

	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }
	public UUID getOrganizationId() { return organizationId; }
	public UUID getMemberId() { return memberId; }
	public String getTokenHash() { return tokenHash; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getLastSeenAt() { return lastSeenAt; }
	public String getUserAgent() { return userAgent; }
	public String getIpAddress() { return ipAddress; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getRevokedAt() { return revokedAt; }
}
