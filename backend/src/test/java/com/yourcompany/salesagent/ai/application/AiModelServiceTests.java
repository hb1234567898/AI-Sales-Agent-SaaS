package com.yourcompany.salesagent.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.yourcompany.salesagent.ai.infrastructure.QwenModelClient;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelProperties;

class AiModelServiceTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T08:00:00Z"), ZoneOffset.UTC);

	@Test
	void reportsMissingApiKeyWithoutCallingProvider() {
		var client = mock(QwenModelClient.class);
		var service = new AiModelService(
				client,
				new QwenModelProperties("", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
				clock);

		var status = service.status();

		assertThat(status.provider()).isEqualTo("QWEN");
		assertThat(status.apiKeyConfigured()).isFalse();
		assertThat(status.ready()).isFalse();
		assertThatThrownBy(service::testConnection).isInstanceOf(AiModelNotConfiguredException.class);
	}

	@Test
	void returnsSuccessfulConnectionResult() {
		var client = mock(QwenModelClient.class);
		when(client.isAvailable()).thenReturn(true);
		when(client.testConnection()).thenReturn("连接成功");
		var service = new AiModelService(
				client,
				new QwenModelProperties("sk-test", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
				clock);

		var response = service.testConnection();

		assertThat(response.status()).isEqualTo("CONNECTED");
		assertThat(response.responsePreview()).isEqualTo("连接成功");
		assertThat(response.latencyMs()).isZero();
	}
}
