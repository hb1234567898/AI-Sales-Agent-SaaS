package com.yourcompany.salesagent.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.yourcompany.salesagent.ai.infrastructure.QwenModelClient;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"app.demo.seed-enabled=false",
		"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
		"spring.datasource.hikari.initialization-fail-timeout=-1"
})
class QwenAiContextTests {

	@Autowired
	private QwenModelClient modelClient;

	@Test
	void createsDynamicQwenClientWithoutStartupApiKey() {
		assertThat(modelClient).isNotNull();
	}
}
