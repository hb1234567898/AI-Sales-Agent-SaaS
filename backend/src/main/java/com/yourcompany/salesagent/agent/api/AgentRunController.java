package com.yourcompany.salesagent.agent.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.agent.application.SalesFollowUpAgentService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.shared.api.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/agent-runs")
public class AgentRunController {

	private final SalesFollowUpAgentService agentService;

	public AgentRunController(SalesFollowUpAgentService agentService) {
		this.agentService = agentService;
	}

	@GetMapping
	public PageResponse<AgentRunResponse> findRuns(
			Authentication authentication,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(agentService.findRuns(principal(authentication), page, size));
	}

	@GetMapping("/{runId}")
	public AgentRunResponse findRun(Authentication authentication, @PathVariable UUID runId) {
		return agentService.findRun(principal(authentication), runId);
	}

	@GetMapping("/{runId}/steps")
	public PageResponse<AgentStepResponse> findSteps(
			Authentication authentication,
			@PathVariable UUID runId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
		return PageResponse.from(agentService.findSteps(principal(authentication), runId, page, size));
	}

	@PostMapping
	public ResponseEntity<AgentRunResponse> run(
			Authentication authentication,
			@Valid @RequestBody(required = false) AgentRunCreateRequest request) {
		var run = agentService.runNow(principal(authentication), request == null ? new AgentRunCreateRequest(null, null, null) : request);
		return ResponseEntity.created(URI.create("/api/v1/agent-runs/" + run.id())).body(run);
	}

	private static AuthPrincipal principal(Authentication authentication) {
		return (AuthPrincipal) authentication.getPrincipal();
	}
}
