package com.yourcompany.salesagent.file.api;

import java.time.Instant;
import java.util.UUID;

import com.yourcompany.salesagent.file.domain.UploadedFile;

public record UploadedFileResponse(
		UUID id,
		UUID customerId,
		String filename,
		String contentType,
		long sizeBytes,
		String sha256,
		Instant createdAt) {

	public static UploadedFileResponse from(UploadedFile file) {
		return new UploadedFileResponse(
				file.getId(),
				file.getCustomerId(),
				file.getOriginalFilename(),
				file.getContentType(),
				file.getSizeBytes(),
				file.getSha256(),
				file.getCreatedAt());
	}
}
