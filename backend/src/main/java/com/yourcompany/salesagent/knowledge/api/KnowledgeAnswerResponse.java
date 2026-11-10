package com.yourcompany.salesagent.knowledge.api;

import java.util.List;
import java.util.UUID;
import com.yourcompany.salesagent.ai.application.ModelUsage;

public record KnowledgeAnswerResponse(String content, List<Source> sources, ModelUsage usage) {
	public record Source(UUID id, UUID documentId, String title, int chunkIndex,
			Integer pageNumber, String excerpt, double score, long revision) {}
}
