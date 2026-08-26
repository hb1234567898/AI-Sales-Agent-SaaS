package com.yourcompany.salesagent.interaction.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.interaction.application.InteractionService;
import com.yourcompany.salesagent.shared.api.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/customers/{customerId}/interactions")
public class InteractionController {

	private final InteractionService interactionService;

	public InteractionController(InteractionService interactionService) {
		this.interactionService = interactionService;
	}

	@GetMapping
	public PageResponse<InteractionResponse> findTimeline(
			@PathVariable UUID customerId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(interactionService.findTimeline(customerId, page, size));
	}

	@PostMapping
	public ResponseEntity<InteractionResponse> create(
			@PathVariable UUID customerId,
			@Valid @RequestBody InteractionCreateRequest request) {
		var interaction = interactionService.create(customerId, request);
		return ResponseEntity.created(interactionUri(customerId, interaction.id())).body(interaction);
	}

	@PostMapping("/chat-import")
	public ResponseEntity<InteractionResponse> importChat(
			@PathVariable UUID customerId,
			@Valid @RequestBody ChatImportRequest request) {
		var interaction = interactionService.importChat(customerId, request);
		return ResponseEntity.created(interactionUri(customerId, interaction.id())).body(interaction);
	}

	private URI interactionUri(UUID customerId, UUID interactionId) {
		return URI.create("/api/v1/customers/" + customerId + "/interactions/" + interactionId);
	}
}
