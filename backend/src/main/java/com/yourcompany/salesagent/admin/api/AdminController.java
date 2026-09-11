package com.yourcompany.salesagent.admin.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import com.yourcompany.salesagent.admin.application.AdminService;
import com.yourcompany.salesagent.admin.domain.MemberRole;
import com.yourcompany.salesagent.admin.domain.MemberStatus;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.shared.api.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class AdminController {

	private final AdminService adminService;

	public AdminController(AdminService adminService) {
		this.adminService = adminService;
	}

	@GetMapping("/members")
	public PageResponse<AdminMemberResponse> findMembers(
			Authentication authentication,
			@RequestParam(defaultValue = "") String keyword,
			@RequestParam(required = false) MemberRole role,
			@RequestParam(required = false) MemberStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(adminService.findMembers(
				principal(authentication), keyword, role, status, page, size));
	}

	@PostMapping("/members")
	public ResponseEntity<AdminMemberResponse> createMember(
			Authentication authentication,
			@Valid @RequestBody AdminMemberCreateRequest request) {
		var member = adminService.createMember(principal(authentication), request);
		return ResponseEntity.created(URI.create("/api/v1/admin/members/" + member.id())).body(member);
	}

	@PutMapping("/members/{memberId}")
	public AdminMemberResponse updateMember(
			Authentication authentication,
			@PathVariable UUID memberId,
			@Valid @RequestBody AdminMemberUpdateRequest request) {
		return adminService.updateMember(principal(authentication), memberId, request);
	}

	@GetMapping("/team")
	public AdminTeamResponse getTeam(Authentication authentication) {
		return adminService.getTeam(principal(authentication));
	}

	@PutMapping("/team")
	public AdminTeamResponse updateTeam(
			Authentication authentication,
			@Valid @RequestBody AdminTeamUpdateRequest request) {
		return adminService.updateTeam(principal(authentication), request);
	}

	private static AuthPrincipal principal(Authentication authentication) {
		return (AuthPrincipal) authentication.getPrincipal();
	}
}
