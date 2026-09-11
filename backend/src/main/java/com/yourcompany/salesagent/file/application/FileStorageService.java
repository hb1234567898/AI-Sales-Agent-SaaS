package com.yourcompany.salesagent.file.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.file.api.UploadedFileResponse;
import com.yourcompany.salesagent.file.domain.UploadedFile;
import com.yourcompany.salesagent.file.infrastructure.UploadedFileMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

	private final UploadedFileMapper mapper;
	private final Clock clock;

	public FileStorageService(UploadedFileMapper mapper, Clock clock) {
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional
	public UploadedFileResponse upload(AuthPrincipal principal, UUID customerId, MultipartFile multipartFile) {
		if (multipartFile == null || multipartFile.isEmpty()) {
			throw new FileStorageException("请选择要上传的附件");
		}
		if (multipartFile.getSize() > MAX_FILE_SIZE) {
			throw new FileStorageException("附件不能超过 10MB");
		}
		try {
			var content = multipartFile.getBytes();
			var filename = cleanFilename(multipartFile.getOriginalFilename());
			var contentType = StringUtils.hasText(multipartFile.getContentType())
					? multipartFile.getContentType().strip()
					: "application/octet-stream";
			var file = UploadedFile.create(
					UUID.randomUUID(),
					principal.organizationId(),
					principal.memberId(),
					customerId,
					filename,
					contentType,
					content,
					sha256(content),
					clock.instant());
			mapper.insert(file);
			return UploadedFileResponse.from(file);
		}
		catch (Exception exception) {
			if (exception instanceof FileStorageException storageException) {
				throw storageException;
			}
			throw new FileStorageException("附件上传失败，请重试", exception);
		}
	}

	@Transactional(readOnly = true)
	public List<UploadedFileResponse> findCustomerFiles(UUID organizationId, UUID customerId) {
		return mapper.selectRecentForCustomer(organizationId, customerId, 20).stream()
				.map(UploadedFileResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<UploadedFile> findRecentForCustomer(UUID organizationId, UUID customerId, int limit) {
		return mapper.selectRecentForCustomer(organizationId, customerId, limit);
	}

	@Transactional(readOnly = true)
	public UploadedFile requireActive(UUID organizationId, UUID fileId) {
		var file = mapper.selectActive(organizationId, fileId);
		if (file == null) {
			throw new FileStorageException("附件不存在或已被删除: " + fileId);
		}
		return file;
	}

	public List<Map<String, Object>> preview(List<UploadedFile> files) {
		return files.stream()
				.map(file -> Map.<String, Object>of(
						"id", file.getId().toString(),
						"name", file.getOriginalFilename(),
						"contentType", file.getContentType(),
						"sizeBytes", file.getSizeBytes()))
				.toList();
	}

	private static String cleanFilename(String value) {
		var filename = StringUtils.hasText(value) ? value.strip() : "attachment";
		filename = filename.replace('\\', '/');
		var index = filename.lastIndexOf('/');
		if (index >= 0) {
			filename = filename.substring(index + 1);
		}
		filename = filename.replaceAll("[\\r\\n\\t]", " ").strip();
		if (!StringUtils.hasText(filename)) {
			return "attachment";
		}
		return filename.length() > 255 ? filename.substring(0, 255) : filename;
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 算法不可用", exception);
		}
	}
}
