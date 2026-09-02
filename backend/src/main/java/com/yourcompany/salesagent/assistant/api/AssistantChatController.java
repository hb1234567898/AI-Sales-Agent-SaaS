package com.yourcompany.salesagent.assistant.api;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.assistant.application.AssistantChatService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/mcp")
public class AssistantChatController {

	private final AssistantChatService chatService;

	public AssistantChatController(AssistantChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping("/chat")
	public AssistantChatResponse chat(Authentication authentication, @Valid @RequestBody AssistantChatRequest request) {
		return chatService.chat((AuthPrincipal) authentication.getPrincipal(), request.message());
	}
}
