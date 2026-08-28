package com.yourcompany.salesagent.audit.api;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.audit.application.AuditLogService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.shared.api.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditLogController {

	private final AuditLogService auditLogService;

	public AuditLogController(AuditLogService auditLogService) {
		this.auditLogService = auditLogService;
	}

	@GetMapping
	public PageResponse<AuditEventResponse> findAuditEvents(
			Authentication authentication,
			@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "") String action,
			@RequestParam(defaultValue = "") String targetType,
			@RequestParam(defaultValue = "") String result,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		var principal = (AuthPrincipal) authentication.getPrincipal();
		return PageResponse.from(auditLogService.findAuditEvents(
				principal.organizationId(), keyword, action, targetType, result, page, size));
	}
}
