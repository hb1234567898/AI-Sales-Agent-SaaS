package com.yourcompany.salesagent.tool.email.domain;

import java.time.Instant;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

@TableName("email_configuration")
public class EmailConfiguration {

	@TableId(value = "organization_id", type = IdType.INPUT)
	private UUID organizationId;

	private String host;

	private int port;

	private String username;

	@TableField("encrypted_password")
	private String encryptedPassword;

	@TableField("from_address")
	private String fromAddress;

	@TableField("smtp_auth")
	private boolean smtpAuth;

	@TableField("starttls_enabled")
	private boolean starttlsEnabled;

	@TableField("starttls_required")
	private boolean starttlsRequired;

	@TableField("encryption_version")
	private int encryptionVersion;

	@TableField("created_at")
	private Instant createdAt;

	@TableField("updated_at")
	private Instant updatedAt;

	@Version
	private long version;

	protected EmailConfiguration() {
	}

	public static EmailConfiguration create(
			UUID organizationId,
			String host,
			int port,
			String username,
			String encryptedPassword,
			String fromAddress,
			boolean smtpAuth,
			boolean starttlsEnabled,
			boolean starttlsRequired,
			Instant now) {
		var configuration = new EmailConfiguration();
		configuration.organizationId = organizationId;
		configuration.host = host;
		configuration.port = port;
		configuration.username = username;
		configuration.encryptedPassword = encryptedPassword;
		configuration.fromAddress = fromAddress;
		configuration.smtpAuth = smtpAuth;
		configuration.starttlsEnabled = starttlsEnabled;
		configuration.starttlsRequired = starttlsRequired;
		configuration.encryptionVersion = 1;
		configuration.createdAt = now;
		configuration.updatedAt = now;
		return configuration;
	}

	public void update(
			String host,
			int port,
			String username,
			String encryptedPassword,
			String fromAddress,
			boolean smtpAuth,
			boolean starttlsEnabled,
			boolean starttlsRequired,
			Instant now) {
		this.host = host;
		this.port = port;
		this.username = username;
		this.encryptedPassword = encryptedPassword;
		this.fromAddress = fromAddress;
		this.smtpAuth = smtpAuth;
		this.starttlsEnabled = starttlsEnabled;
		this.starttlsRequired = starttlsRequired;
		this.updatedAt = now;
	}

	public UUID getOrganizationId() { return organizationId; }
	public String getHost() { return host; }
	public int getPort() { return port; }
	public String getUsername() { return username; }
	public String getEncryptedPassword() { return encryptedPassword; }
	public String getFromAddress() { return fromAddress; }
	public boolean isSmtpAuth() { return smtpAuth; }
	public boolean isStarttlsEnabled() { return starttlsEnabled; }
	public boolean isStarttlsRequired() { return starttlsRequired; }
	public int getEncryptionVersion() { return encryptionVersion; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
