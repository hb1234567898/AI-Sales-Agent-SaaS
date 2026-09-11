package com.yourcompany.salesagent.file.domain;

import java.time.Instant;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

@TableName("uploaded_file")
public class UploadedFile {

	@TableId(value = "id", type = IdType.INPUT)
	private UUID id;

	@TableField("organization_id")
	private UUID organizationId;

	@TableField("uploaded_by_member_id")
	private UUID uploadedByMemberId;

	@TableField("customer_id")
	private UUID customerId;

	@TableField("original_filename")
	private String originalFilename;

	@TableField("content_type")
	private String contentType;

	@TableField("size_bytes")
	private long sizeBytes;

	private String sha256;

	private byte[] content;

	private String status;

	@TableField("created_at")
	private Instant createdAt;

	@TableField("updated_at")
	private Instant updatedAt;

	@Version
	private long version;

	protected UploadedFile() {
	}

	public static UploadedFile create(
			UUID id,
			UUID organizationId,
			UUID uploadedByMemberId,
			UUID customerId,
			String originalFilename,
			String contentType,
			byte[] content,
			String sha256,
			Instant now) {
		var file = new UploadedFile();
		file.id = id;
		file.organizationId = organizationId;
		file.uploadedByMemberId = uploadedByMemberId;
		file.customerId = customerId;
		file.originalFilename = originalFilename;
		file.contentType = contentType;
		file.sizeBytes = content.length;
		file.content = content;
		file.sha256 = sha256;
		file.status = "ACTIVE";
		file.createdAt = now;
		file.updatedAt = now;
		return file;
	}

	public UUID getId() { return id; }
	public UUID getOrganizationId() { return organizationId; }
	public UUID getUploadedByMemberId() { return uploadedByMemberId; }
	public UUID getCustomerId() { return customerId; }
	public String getOriginalFilename() { return originalFilename; }
	public String getContentType() { return contentType; }
	public long getSizeBytes() { return sizeBytes; }
	public String getSha256() { return sha256; }
	public byte[] getContent() { return content; }
	public String getStatus() { return status; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
