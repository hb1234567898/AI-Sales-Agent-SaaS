package com.yourcompany.salesagent.knowledge.api;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record KnowledgeQueryRequest(@NotBlank @Size(max = 2000) String question, UUID documentId) {}
