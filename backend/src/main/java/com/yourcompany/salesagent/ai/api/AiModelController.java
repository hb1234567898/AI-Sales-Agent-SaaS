package com.yourcompany.salesagent.ai.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.ai.application.AiModelService;

@RestController
@RequestMapping("/api/v1/ai/model")
public class AiModelController {

	private final AiModelService modelService;

	public AiModelController(AiModelService modelService) {
		this.modelService = modelService;
	}

	@GetMapping
	public AiModelStatusResponse status() {
		return modelService.status();
	}

	@PostMapping("/test")
	public AiModelTestResponse testConnection() {
		return modelService.testConnection();
	}
}
