package com.yourcompany.salesagent.system.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class SystemControllerTests {

	@Test
	void returnsStableHealthPayload() {
		var instant = Instant.parse("2026-08-22T08:00:00Z");
		var controller = new SystemController(Clock.fixed(instant, ZoneOffset.UTC));

		var response = controller.health();

		assertThat(response.service()).isEqualTo("sales-agent");
		assertThat(response.status()).isEqualTo("UP");
		assertThat(response.timestamp()).isEqualTo(instant);
	}
}
