package com.yourcompany.salesagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"app.demo.seed-enabled=false",
		"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
		"spring.datasource.hikari.initialization-fail-timeout=-1"
})
class SalesAgentApplicationTests {

	@Test
	void startsWithoutAiCredentials() {
	}
}
