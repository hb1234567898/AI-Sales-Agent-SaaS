package com.yourcompany.salesagent.knowledge.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record KnowledgeTextRequest(@NotNull @Valid KnowledgeDocumentRequest metadata,
		@NotBlank @Size(max = 500000) String content) {}
