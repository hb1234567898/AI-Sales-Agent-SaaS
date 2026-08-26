package com.yourcompany.salesagent.ai.application;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.yourcompany.salesagent.ai.api.AiModelStatusResponse;
import com.yourcompany.salesagent.ai.api.AiModelTestResponse;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelClient;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelProperties;

@Service
public class AiModelService {

	private final QwenModelClient modelClient;
	private final QwenModelProperties properties;
	private final Clock clock;

	public AiModelService(QwenModelClient modelClient, QwenModelProperties properties, Clock clock) {
		this.modelClient = modelClient;
		this.properties = properties;
		this.clock = clock;
	}

	public AiModelStatusResponse status() {
		var apiKeyConfigured = StringUtils.hasText(properties.apiKey());
		var ready = apiKeyConfigured && modelClient.isAvailable();
		var status = ready ? "READY" : apiKeyConfigured ? "DISABLED" : "MISSING_API_KEY";
		return new AiModelStatusResponse(
				"QWEN",
				properties.model(),
				properties.baseUrl(),
				apiKeyConfigured,
				ready,
				status);
	}

	public AiModelTestResponse testConnection() {
		var currentStatus = status();
		if (!currentStatus.apiKeyConfigured()) {
			throw new AiModelNotConfiguredException("尚未配置 QWEN_API_KEY");
		}
		if (!currentStatus.ready()) {
			throw new AiModelNotConfiguredException("模型客户端未启用，请设置 AI_CHAT_PROVIDER=openai 后重启后端");
		}

		var startedAt = clock.millis();
		try {
			var content = modelClient.testConnection();
			return new AiModelTestResponse(
					"QWEN",
					properties.model(),
					"CONNECTED",
					content == null ? "" : content.strip(),
					Math.max(0, clock.millis() - startedAt));
		}
		catch (RuntimeException exception) {
			throw new AiModelConnectionException("千问模型连接失败，请检查 API Key、Base URL、模型名称和服务器网络", exception);
		}
	}
}
