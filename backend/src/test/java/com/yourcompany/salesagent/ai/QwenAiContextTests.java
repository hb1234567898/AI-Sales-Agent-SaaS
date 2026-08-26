package com.yourcompany.salesagent.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.yourcompany.salesagent.ai.application.AiModelService;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"app.demo.seed-enabled=false",
		"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
		"spring.datasource.hikari.initialization-fail-timeout=-1",
		"spring.ai.model.chat=openai",
		"spring.ai.openai.api-key=sk-context-test",
		"app.ai.qwen.api-key=sk-context-test"
})
class QwenAiContextTests {

	@Autowired
	private AiModelService modelService;

	@Test
	void createsChatClientWhenQwenIsEnabled() {
		var status = modelService.status();

		assertThat(status.apiKeyConfigured()).isTrue();
		assertThat(status.ready()).isTrue();
		assertThat(status.model()).isEqualTo("qwen-plus");
	}
}
