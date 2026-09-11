package com.yourcompany.salesagent.approval.api;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.approval.application.ApprovalService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.shared.api.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

	private final ApprovalService approvalService;

	public ApprovalController(ApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@GetMapping
	public PageResponse<ApprovalResponse> findApprovals(
			@RequestParam(defaultValue = "PENDING") String status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(approvalService.findApprovals(status, page, size));
	}

	@PostMapping("/{approvalId}/approve")
	public ApprovalResponse approve(
			Authentication authentication,
			@PathVariable UUID approvalId,
			@Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
		return approvalService.approve(principal(authentication), approvalId, request == null ? new ApprovalDecisionRequest(null, null) : request);
	}

	@PostMapping("/{approvalId}/reject")
	public ApprovalResponse reject(
			Authentication authentication,
			@PathVariable UUID approvalId,
			@Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
		return approvalService.reject(principal(authentication), approvalId, request == null ? new ApprovalDecisionRequest(null, null) : request);
	}

	private static AuthPrincipal principal(Authentication authentication) {
		return (AuthPrincipal) authentication.getPrincipal();
	}
}
