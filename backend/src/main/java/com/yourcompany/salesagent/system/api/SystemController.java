package com.yourcompany.salesagent.system.api;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

	private final Clock clock;

	public SystemController(Clock clock) {
		this.clock = clock;
	}

	@GetMapping("/health")
	public HealthResponse health() {
		return new HealthResponse("sales-agent", "UP", Instant.now(clock));
	}
}
