package com.yourcompany.salesagent.knowledge.api;

import java.time.Instant;
import java.util.UUID;
import com.yourcompany.salesagent.knowledge.infrastructure.KnowledgeDocumentRow;

public record KnowledgeDocumentResponse(UUID id, String title, String category, String visibility, String status,
		boolean enabled, int chunkCount, int characterCount, String errorMessage, long revision, long version,
		String filename, long sizeBytes, String uploadedByName, Instant createdAt, Instant updatedAt, boolean canManage) {
	public static KnowledgeDocumentResponse from(KnowledgeDocumentRow r, boolean canManage) {
		return new KnowledgeDocumentResponse(r.getId(), r.getTitle(), r.getCategory(), r.getVisibility(), r.getStatus(),
				r.isEnabled(), r.getChunkCount(), r.getCharacterCount(), r.getErrorMessage(), r.getRevision(), r.getVersion(),
				r.getFilename(), r.getSizeBytes(), r.getUploadedByName(), r.getCreatedAt(), r.getUpdatedAt(), canManage);
	}
}
