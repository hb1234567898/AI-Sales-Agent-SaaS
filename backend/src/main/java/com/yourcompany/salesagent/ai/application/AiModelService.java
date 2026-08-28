package com.yourcompany.salesagent.ai.application;

import java.net.URI;
import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yourcompany.salesagent.ai.api.AiModelStatusResponse;
import com.yourcompany.salesagent.ai.api.AiModelTestResponse;
import com.yourcompany.salesagent.ai.api.AiModelUpdateRequest;
import com.yourcompany.salesagent.ai.domain.AiModelConfiguration;
import com.yourcompany.salesagent.ai.infrastructure.AiModelConfigurationMapper;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelClient;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelProperties;
import com.yourcompany.salesagent.shared.security.SecretCipher;
import com.yourcompany.salesagent.shared.security.SecretEncryptionException;

@Service
public class AiModelService {

	private final QwenModelClient modelClient;
	private final AiModelConfigurationMapper configurationMapper;
	private final SecretCipher secretCipher;
	private final QwenModelProperties properties;
	private final Clock clock;

	public AiModelService(
			QwenModelClient modelClient,
			AiModelConfigurationMapper configurationMapper,
			SecretCipher secretCipher,
			QwenModelProperties properties,
			Clock clock) {
		this.modelClient = modelClient;
		this.configurationMapper = configurationMapper;
		this.secretCipher = secretCipher;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AiModelStatusResponse status(UUID organizationId) {
		var configuration = configurationMapper.selectById(organizationId);
		if (configuration == null) {
			return new AiModelStatusResponse(
					"QWEN",
					properties.model(),
					properties.baseUrl(),
					false,
					false,
					"MISSING_API_KEY");
		}
		var ready = canDecrypt(configuration);
		return new AiModelStatusResponse(
				configuration.getProvider(),
				configuration.getModelName(),
				configuration.getBaseUrl(),
				true,
				ready,
				ready ? "READY" : "ENCRYPTION_KEY_UNAVAILABLE");
	}

	@Transactional
	public AiModelStatusResponse update(UUID organizationId, AiModelUpdateRequest request) {
		validateBaseUrl(request.baseUrl());
		var configuration = configurationMapper.selectById(organizationId);
		var encryptedApiKey = configuration == null ? null : configuration.getEncryptedApiKey();
		if (StringUtils.hasText(request.apiKey())) {
			encryptedApiKey = secretCipher.encrypt(organizationId, request.apiKey().strip());
		}
		if (!StringUtils.hasText(encryptedApiKey)) {
			throw new AiModelConfigurationException("首次保存时必须输入 API Key");
		}
		var now = clock.instant();
		if (configuration == null) {
			configurationMapper.insert(AiModelConfiguration.create(
					organizationId,
					request.model().strip(),
					request.baseUrl().strip(),
					encryptedApiKey,
					now));
		}
		else {
			configuration.update(request.model().strip(), request.baseUrl().strip(), encryptedApiKey, now);
			configurationMapper.updateById(configuration);
		}
		return status(organizationId);
	}

	@Transactional(readOnly = true)
	public AiModelTestResponse testConnection(UUID organizationId) {
		var configuration = requireRuntimeConfiguration(organizationId);
		var startedAt = clock.millis();
		try {
			var content = modelClient.testConnection(configuration);
			return new AiModelTestResponse(
					"QWEN",
					configuration.model(),
					"CONNECTED",
					content == null ? "" : content.strip(),
					Math.max(0, clock.millis() - startedAt));
		}
		catch (RuntimeException exception) {
			throw new AiModelConnectionException("千问模型连接失败，请检查 API Key、Base URL、模型名称和服务器网络", exception);
		}
	}

	@Transactional(readOnly = true)
	public AiModelRuntimeConfiguration requireRuntimeConfiguration(UUID organizationId) {
		var configuration = configurationMapper.selectById(organizationId);
		if (configuration == null) {
			throw new AiModelNotConfiguredException("尚未保存模型 API Key");
		}
		return new AiModelRuntimeConfiguration(
				configuration.getProvider(),
				configuration.getModelName(),
				configuration.getBaseUrl(),
				secretCipher.decrypt(organizationId, configuration.getEncryptedApiKey()));
	}

	private boolean canDecrypt(AiModelConfiguration configuration) {
		try {
			return StringUtils.hasText(secretCipher.decrypt(
					configuration.getOrganizationId(), configuration.getEncryptedApiKey()));
		}
		catch (SecretEncryptionException exception) {
			return false;
		}
	}

	private void validateBaseUrl(String value) {
		try {
			var uri = URI.create(value.strip());
			if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
					|| uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
				throw new IllegalArgumentException();
			}
		}
		catch (IllegalArgumentException exception) {
			throw new AiModelConfigurationException("API 地址必须是有效的 HTTPS 地址");
		}
	}
}
