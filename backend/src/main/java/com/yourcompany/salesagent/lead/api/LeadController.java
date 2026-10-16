package com.yourcompany.salesagent.lead.api;

import java.net.URI;
import java.util.UUID;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.lead.application.LeadService;
import com.yourcompany.salesagent.lead.domain.LeadStatus;
import com.yourcompany.salesagent.shared.api.PageResponse;
import com.yourcompany.salesagent.customer.api.OwnerOptionResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {
	private final LeadService service;

	public LeadController(LeadService service) {
		this.service = service;
	}

	@GetMapping
	public PageResponse<LeadResponse> find(Authentication authentication, @RequestParam(defaultValue = "") String query,
			@RequestParam(required = false) LeadStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(service.findLeads(principal(authentication), query, status, page, size));
	}

	@GetMapping("/{leadId}")
	public LeadResponse findOne(Authentication authentication, @PathVariable UUID leadId) {
		return service.findLead(principal(authentication), leadId);
	}

	@GetMapping("/metrics")
	public LeadMetricsResponse metrics(Authentication authentication) {
		return service.metrics(principal(authentication));
	}

	@GetMapping("/owners")
	public List<OwnerOptionResponse> owners(Authentication authentication) {
		return service.owners(principal(authentication));
	}

	@PostMapping
	public ResponseEntity<LeadResponse> create(Authentication authentication, @Valid @RequestBody LeadUpsertRequest request) {
		var lead = service.createLead(principal(authentication), request);
		return ResponseEntity.created(URI.create("/api/v1/leads/" + lead.id())).body(lead);
	}

	@PutMapping("/{leadId}")
	public LeadResponse update(Authentication authentication, @PathVariable UUID leadId,
			@Valid @RequestBody LeadUpsertRequest request) {
		return service.updateLead(principal(authentication), leadId, request);
	}

	@PutMapping("/{leadId}/owner")
	public LeadResponse assign(Authentication authentication, @PathVariable UUID leadId,
			@Valid @RequestBody LeadAssignRequest request) {
		return service.assign(principal(authentication), leadId, request);
	}

	@PostMapping("/{leadId}/convert")
	public LeadConversionResponse convert(Authentication authentication, @PathVariable UUID leadId,
			@Valid @RequestBody LeadConvertRequest request) {
		return service.convert(principal(authentication), leadId, request);
	}

	@PostMapping("/import")
	public LeadImportResponse importLeads(Authentication authentication, @Valid @RequestBody LeadImportRequest request) {
		return service.importLeads(principal(authentication), request);
	}

	private static AuthPrincipal principal(Authentication authentication) {
		return (AuthPrincipal) authentication.getPrincipal();
	}
}
