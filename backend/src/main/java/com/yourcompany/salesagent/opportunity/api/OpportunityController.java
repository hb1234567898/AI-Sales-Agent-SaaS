package com.yourcompany.salesagent.opportunity.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.opportunity.application.OpportunityService;
import com.yourcompany.salesagent.opportunity.domain.OpportunityStage;
import com.yourcompany.salesagent.shared.api.PageResponse;

@Validated
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {
	private final OpportunityService service;
	public OpportunityController(OpportunityService service) { this.service = service; }
	@GetMapping
	public PageResponse<OpportunityResponse> find(Authentication authentication,
			@RequestParam(defaultValue = "") @Size(max = 255) String query,
			@RequestParam(required = false) OpportunityStage stage, @RequestParam(required = false) UUID owner,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		return PageResponse.from(service.find(principal(authentication), query, stage, owner, page, size));
	}
	@GetMapping("/metrics")
	public OpportunityMetricsResponse metrics(Authentication authentication) { return service.metrics(principal(authentication)); }
	@GetMapping("/{id}")
	public OpportunityResponse get(Authentication authentication, @PathVariable UUID id) { return service.get(principal(authentication), id); }
	@GetMapping("/{id}/history")
	public List<OpportunityEventResponse> history(Authentication authentication, @PathVariable UUID id) { return service.history(principal(authentication), id); }
	@PostMapping
	public ResponseEntity<OpportunityResponse> create(Authentication authentication, @Valid @RequestBody OpportunityUpsertRequest input) {
		var result = service.create(principal(authentication), input);
		return ResponseEntity.created(URI.create("/api/v1/opportunities/" + result.id())).body(result);
	}
	@PutMapping("/{id}")
	public OpportunityResponse update(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody OpportunityUpsertRequest input) {
		return service.update(principal(authentication), id, input);
	}
	@PutMapping("/{id}/stage")
	public OpportunityResponse stage(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody OpportunityStageRequest input) {
		return service.changeStage(principal(authentication), id, input);
	}
	private static AuthPrincipal principal(Authentication authentication) { return (AuthPrincipal) authentication.getPrincipal(); }
}
