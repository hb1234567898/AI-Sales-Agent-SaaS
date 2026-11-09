package com.yourcompany.salesagent.knowledge.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.knowledge.application.KnowledgeService;
import com.yourcompany.salesagent.shared.api.PageResponse;

@Validated
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
	private final KnowledgeService service;
	public KnowledgeController(KnowledgeService service) { this.service = service; }
	@GetMapping("/documents")
	public PageResponse<KnowledgeDocumentResponse> find(Authentication authentication,
			@RequestParam(defaultValue = "") @Size(max = 255) String query, @RequestParam(defaultValue = "") String category,
			@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(service.find(actor(authentication), query, category, page, size));
	}
	@GetMapping("/documents/{id}")
	public KnowledgeDocumentResponse get(Authentication authentication, @PathVariable UUID id) { return service.get(actor(authentication), id); }
	@GetMapping("/documents/{id}/chunks")
	public PageResponse<KnowledgeChunkResponse> chunks(Authentication authentication, @PathVariable UUID id,
			@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(service.chunks(actor(authentication), id, page, size));
	}
	@GetMapping("/documents/{id}/file")
	public ResponseEntity<byte[]> download(Authentication authentication, @PathVariable UUID id) {
		var file = service.download(actor(authentication), id);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store").body(file.getContent());
	}
	@PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public KnowledgeDocumentResponse upload(Authentication authentication, @Valid @RequestPart("metadata") KnowledgeDocumentRequest metadata, @RequestPart("file") MultipartFile file) {
		return service.upload(actor(authentication), metadata, file);
	}
	@PostMapping("/documents/text")
	public KnowledgeDocumentResponse text(Authentication authentication, @Valid @RequestBody KnowledgeTextRequest request) { return service.createText(actor(authentication), request); }
	@PutMapping("/documents/{id}")
	public KnowledgeDocumentResponse edit(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody KnowledgeDocumentRequest input) { return service.edit(actor(authentication), id, input); }
	@PutMapping("/documents/{id}/state")
	public KnowledgeDocumentResponse state(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody KnowledgeStateRequest input) { return service.state(actor(authentication), id, input); }
	@PostMapping("/documents/{id}/reprocess")
	public KnowledgeDocumentResponse reprocess(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody KnowledgeVersionRequest input) { return service.reprocess(actor(authentication), id, input.version(), null); }
	@PostMapping(value = "/documents/{id}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public KnowledgeDocumentResponse replace(Authentication authentication, @PathVariable UUID id, @RequestParam @Min(0) long version, @RequestPart("file") MultipartFile file) { return service.reprocess(actor(authentication), id, version, file); }
	@DeleteMapping("/documents/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(Authentication authentication, @PathVariable UUID id, @RequestParam @Min(0) long version) { service.delete(actor(authentication), id, version); }
	@PostMapping("/query")
	public KnowledgeAnswerResponse query(Authentication authentication, @Valid @RequestBody KnowledgeQueryRequest input) { return service.ask(actor(authentication), input); }
	private static AuthPrincipal actor(Authentication authentication) { return (AuthPrincipal) authentication.getPrincipal(); }
}
