package com.yourcompany.salesagent.interaction.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.interaction.application.ChatAnalysisService;
import com.yourcompany.salesagent.interaction.application.InteractionService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.shared.api.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/customers/{customerId}/interactions")
@PreAuthorize("isAuthenticated()")
public class InteractionController {

	private final InteractionService interactionService;
	private final ChatAnalysisService chatAnalysisService;

	public InteractionController(InteractionService interactionService, ChatAnalysisService chatAnalysisService) {
		this.interactionService = interactionService;
		this.chatAnalysisService = chatAnalysisService;
	}

	@GetMapping
	public PageResponse<InteractionResponse> findTimeline(
			@AuthenticationPrincipal AuthPrincipal actor,
			@PathVariable UUID customerId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(interactionService.findTimeline(actor, customerId, page, size));
	}

	@PostMapping
	public ResponseEntity<InteractionResponse> create(
			@AuthenticationPrincipal AuthPrincipal actor,
			@PathVariable UUID customerId,
			@Valid @RequestBody InteractionCreateRequest request) {
		var interaction = interactionService.create(actor, customerId, request);
		return ResponseEntity.created(interactionUri(customerId, interaction.id())).body(interaction);
	}

	@PostMapping("/chat-import")
	public ResponseEntity<InteractionResponse> importChat(
			@AuthenticationPrincipal AuthPrincipal actor,
			@PathVariable UUID customerId,
			@Valid @RequestBody ChatImportRequest request) {
		var interaction = interactionService.importChat(actor, customerId, request);
		return ResponseEntity.created(interactionUri(customerId, interaction.id())).body(interaction);
	}

	@GetMapping("/analyses")
	public List<ChatAnalysisResponse> findLatestAnalyses(@AuthenticationPrincipal AuthPrincipal actor, @PathVariable UUID customerId) {
		return chatAnalysisService.findLatestForCustomer(actor, customerId);
	}

	@PostMapping("/{interactionId}/analysis")
	public ResponseEntity<ChatAnalysisResponse> analyzeChat(
			@AuthenticationPrincipal AuthPrincipal actor,
			@PathVariable UUID customerId,
			@PathVariable UUID interactionId) {
		var analysis = chatAnalysisService.analyze(actor, customerId, interactionId);
		return ResponseEntity.created(URI.create(interactionUri(customerId, interactionId) + "/analysis/" + analysis.id()))
				.body(analysis);
	}

	@PostMapping("/{interactionId}/analysis/{analysisId}/apply")
	public ChatAnalysisResponse applyAnalysis(
			@AuthenticationPrincipal AuthPrincipal actor,
			@PathVariable UUID customerId,
			@PathVariable UUID interactionId,
			@PathVariable UUID analysisId) {
		return chatAnalysisService.apply(actor, customerId, interactionId, analysisId);
	}

	private URI interactionUri(UUID customerId, UUID interactionId) {
		return URI.create("/api/v1/customers/" + customerId + "/interactions/" + interactionId);
	}
}
