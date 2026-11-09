package com.yourcompany.salesagent.knowledge.api;

import java.util.UUID;

public record KnowledgeChunkResponse(UUID id, UUID documentId, int chunkIndex, Integer pageNumber,
		String title, String content, long revision) {}
