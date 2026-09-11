package com.yourcompany.salesagent.file.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.file.application.FileStorageException;
import com.yourcompany.salesagent.file.application.FileStorageService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

	private final FileStorageService service;
	private final UUID demoOrganizationId;

	public FileController(
			FileStorageService service,
			@Value("${app.demo.organization-id}") UUID demoOrganizationId) {
		this.service = service;
		this.demoOrganizationId = demoOrganizationId;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<UploadedFileResponse> upload(
			Authentication authentication,
			@RequestParam(required = false) UUID customerId,
			@RequestPart("file") MultipartFile file) {
		var uploaded = service.upload(principal(authentication), customerId, file);
		return ResponseEntity.created(URI.create("/api/v1/files/" + uploaded.id())).body(uploaded);
	}

	@GetMapping
	public List<UploadedFileResponse> findFiles(
			Authentication authentication,
			@RequestParam UUID customerId) {
		return service.findCustomerFiles(organizationId(authentication), customerId);
	}

	private AuthPrincipal principal(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
			return principal;
		}
		throw new FileStorageException("请先登录后再上传附件");
	}

	private UUID organizationId(Authentication authentication) {
		return authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal
				? principal.organizationId()
				: demoOrganizationId;
	}
}
