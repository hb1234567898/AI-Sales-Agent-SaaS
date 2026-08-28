package com.yourcompany.salesagent.ai.domain;

import java.time.Instant;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

@TableName("ai_model_configuration")
public class AiModelConfiguration {

	@TableId(value = "organization_id", type = IdType.INPUT)
	private UUID organizationId;

	private String provider;

	@TableField("model_name")
	private String modelName;

	@TableField("base_url")
	private String baseUrl;

	@TableField("encrypted_api_key")
	private String encryptedApiKey;

	@TableField("encryption_version")
	private int encryptionVersion;

	@TableField("created_at")
	private Instant createdAt;

	@TableField("updated_at")
	private Instant updatedAt;

	@Version
	private long version;

	protected AiModelConfiguration() {
	}

	public static AiModelConfiguration create(
			UUID organizationId,
			String modelName,
			String baseUrl,
			String encryptedApiKey,
			Instant now) {
		var configuration = new AiModelConfiguration();
		configuration.organizationId = organizationId;
		configuration.provider = "QWEN";
		configuration.modelName = modelName;
		configuration.baseUrl = baseUrl;
		configuration.encryptedApiKey = encryptedApiKey;
		configuration.encryptionVersion = 1;
		configuration.createdAt = now;
		configuration.updatedAt = now;
		return configuration;
	}

	public void update(String modelName, String baseUrl, String encryptedApiKey, Instant now) {
		this.modelName = modelName;
		this.baseUrl = baseUrl;
		this.encryptedApiKey = encryptedApiKey;
		this.updatedAt = now;
	}

	public UUID getOrganizationId() { return organizationId; }
	public String getProvider() { return provider; }
	public String getModelName() { return modelName; }
	public String getBaseUrl() { return baseUrl; }
	public String getEncryptedApiKey() { return encryptedApiKey; }
	public int getEncryptionVersion() { return encryptionVersion; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
