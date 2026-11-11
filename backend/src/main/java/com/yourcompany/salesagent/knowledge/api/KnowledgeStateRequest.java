package com.yourcompany.salesagent.knowledge.api;

import jakarta.validation.constraints.*;

public record KnowledgeStateRequest(boolean enabled, @NotNull @Min(0) Long version) {}
