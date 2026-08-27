package com.yourcompany.salesagent.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.yourcompany.salesagent.ai.api.AiModelUpdateRequest;
import com.yourcompany.salesagent.ai.domain.AiModelConfiguration;
import com.yourcompany.salesagent.ai.infrastructure.AiModelConfigurationMapper;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelClient;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelProperties;
import com.yourcompany.salesagent.shared.security.SecretCipher;

class AiModelServiceTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T08:00:00Z"), ZoneOffset.UTC);
	private final UUID organizationId = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Test
	void reportsMissingApiKeyWithoutCallingProvider() {
		var client = mock(QwenModelClient.class);
		var mapper = mock(AiModelConfigurationMapper.class);
		var cipher = mock(SecretCipher.class);
		var service = new AiModelService(
				client,
				mapper,
				cipher,
				new QwenModelProperties("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
				clock);

		var status = service.status(organizationId);

		assertThat(status.provider()).isEqualTo("QWEN");
		assertThat(status.apiKeyConfigured()).isFalse();
		assertThat(status.ready()).isFalse();
		assertThatThrownBy(() -> service.testConnection(organizationId)).isInstanceOf(AiModelNotConfiguredException.class);
	}

	@Test
	void returnsSuccessfulConnectionResult() {
		var client = mock(QwenModelClient.class);
		var mapper = mock(AiModelConfigurationMapper.class);
		var cipher = mock(SecretCipher.class);
		var configuration = AiModelConfiguration.create(
				organizationId,
				"qwen-plus",
				"https://dashscope.aliyuncs.com/compatible-mode/v1",
				"encrypted-key",
				clock.instant());
		when(mapper.selectById(organizationId)).thenReturn(configuration);
		when(cipher.decrypt(organizationId, "encrypted-key")).thenReturn("sk-test");
		when(client.testConnection(any())).thenReturn("连接成功");
		var service = new AiModelService(
				client,
				mapper,
				cipher,
				new QwenModelProperties("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
				clock);

		var response = service.testConnection(organizationId);

		assertThat(response.status()).isEqualTo("CONNECTED");
		assertThat(response.responsePreview()).isEqualTo("连接成功");
		assertThat(response.latencyMs()).isZero();
	}

	@Test
	void encryptsApiKeyBeforeSavingConfiguration() {
		var client = mock(QwenModelClient.class);
		var mapper = mock(AiModelConfigurationMapper.class);
		var cipher = mock(SecretCipher.class);
		when(cipher.encrypt(organizationId, "sk-manual")).thenReturn("v1.encrypted");
		var service = new AiModelService(
				client,
				mapper,
				cipher,
				new QwenModelProperties("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
				clock);

		service.update(organizationId, new AiModelUpdateRequest(
				"QWEN",
				"qwen3.7-plus",
				"https://dashscope.aliyuncs.com/compatible-mode/v1",
				"sk-manual"));

		verify(cipher).encrypt(organizationId, "sk-manual");
		verify(mapper).insert(any(AiModelConfiguration.class));
	}
}
