package com.yourcompany.salesagent.assistant.api;

import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.yourcompany.salesagent.assistant.application.AssistantStreamTransport;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import com.yourcompany.salesagent.assistant.application.AssistantChatService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.assistant.api.AssistantConversationResponse;
import com.yourcompany.salesagent.assistant.api.AssistantMessageResponse;
import com.yourcompany.salesagent.shared.api.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/mcp")
public class AssistantChatController {

	private final AssistantChatService chatService;
	private final AssistantStreamTransport streamTransport;

	public AssistantChatController(AssistantChatService chatService, AssistantStreamTransport streamTransport) {
		this.chatService = chatService;
		this.streamTransport = streamTransport;
	}

	@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> stream(Authentication authentication, @Valid @RequestBody AssistantChatRequest request) {
		return ResponseEntity.ok()
				.header("Cache-Control", "no-cache, no-transform")
				.header("X-Accel-Buffering", "no")
				.body(streamTransport.open(principal(authentication), request));
	}

	@PostMapping("/chat")
	public AssistantChatResponse chat(Authentication authentication, @Valid @RequestBody AssistantChatRequest request) {
		return chatService.chat(principal(authentication), request);
	}

	@GetMapping("/conversations")
	public PageResponse<AssistantConversationResponse> findConversations(
			Authentication authentication,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(chatService.findConversations(principal(authentication), page, size));
	}

	@GetMapping("/conversations/{conversationId}/messages")
	public PageResponse<AssistantMessageResponse> findMessages(
			Authentication authentication,
			@PathVariable UUID conversationId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
		return PageResponse.from(chatService.findMessages(principal(authentication), conversationId, page, size));
	}

	private static AuthPrincipal principal(Authentication authentication) {
		return (AuthPrincipal) authentication.getPrincipal();
	}
}
