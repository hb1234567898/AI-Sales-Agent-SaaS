package com.yourcompany.salesagent.followup.api;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.followup.application.FollowUpService;
import com.yourcompany.salesagent.shared.api.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/follow-ups")
public class FollowUpController {

	private final FollowUpService followUpService;

	public FollowUpController(FollowUpService followUpService) {
		this.followUpService = followUpService;
	}

	@GetMapping
	public PageResponse<FollowUpResponse> findFollowUps(
			@RequestParam(defaultValue = "ALL") String filter,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(followUpService.findFollowUps(filter, page, size));
	}

	@PostMapping("/{followUpId}/complete")
	public FollowUpResponse complete(Authentication authentication, @PathVariable UUID followUpId) {
		return followUpService.complete(principal(authentication), followUpId);
	}

	private static AuthPrincipal principal(Authentication authentication) {
		return (AuthPrincipal) authentication.getPrincipal();
	}
}
