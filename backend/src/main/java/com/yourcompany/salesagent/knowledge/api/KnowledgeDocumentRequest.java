package com.yourcompany.salesagent.knowledge.api;

import jakarta.validation.constraints.*;

public record KnowledgeDocumentRequest(
		@NotBlank @Size(max = 255) String title,
		@NotNull @Pattern(regexp = "PRODUCT|PRICING|PLAYBOOK|CASE_STUDY|POLICY|OTHER") String category,
		@NotNull @Pattern(regexp = "TEAM|PRIVATE") String visibility,
		@Min(0) Long version) {}
