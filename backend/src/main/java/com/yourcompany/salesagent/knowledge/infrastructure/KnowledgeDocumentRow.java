package com.yourcompany.salesagent.knowledge.infrastructure;

import java.time.Instant;
import java.util.UUID;

public class KnowledgeDocumentRow {
	private UUID id;
	private UUID uploadedByMemberId;
	private UUID fileId;
	private String title;
	private String category;
	private String visibility;
	private String status;
	private boolean enabled;
	private int chunkCount;
	private int characterCount;
	private String errorMessage;
	private long revision;
	private long version;
	private String filename;
	private long sizeBytes;
	private String uploadedByName;
	private Instant createdAt;
	private Instant updatedAt;
	public UUID getId() { return id; }
	public void setId(UUID v) { id = v; }
	public UUID getUploadedByMemberId() { return uploadedByMemberId; }
	public void setUploadedByMemberId(UUID v) { uploadedByMemberId = v; }
	public UUID getFileId() { return fileId; }
	public void setFileId(UUID v) { fileId = v; }
	public String getTitle() { return title; }
	public void setTitle(String v) { title = v; }
	public String getCategory() { return category; }
	public void setCategory(String v) { category = v; }
	public String getVisibility() { return visibility; }
	public void setVisibility(String v) { visibility = v; }
	public String getStatus() { return status; }
	public void setStatus(String v) { status = v; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean v) { enabled = v; }
	public int getChunkCount() { return chunkCount; }
	public void setChunkCount(int v) { chunkCount = v; }
	public int getCharacterCount() { return characterCount; }
	public void setCharacterCount(int v) { characterCount = v; }
	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String v) { errorMessage = v; }
	public long getRevision() { return revision; }
	public void setRevision(long v) { revision = v; }
	public long getVersion() { return version; }
	public void setVersion(long v) { version = v; }
	public String getFilename() { return filename; }
	public void setFilename(String v) { filename = v; }
	public long getSizeBytes() { return sizeBytes; }
	public void setSizeBytes(long v) { sizeBytes = v; }
	public String getUploadedByName() { return uploadedByName; }
	public void setUploadedByName(String v) { uploadedByName = v; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant v) { createdAt = v; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant v) { updatedAt = v; }
}
