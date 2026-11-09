package com.yourcompany.salesagent.knowledge.api;

import jakarta.validation.constraints.*;

public record KnowledgeVersionRequest(@NotNull @Min(0) Long version) {}
