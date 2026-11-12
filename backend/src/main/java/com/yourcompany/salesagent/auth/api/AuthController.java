package com.yourcompany.salesagent.auth.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.auth.application.AuthService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping("/session")
	AuthSessionResponse session(Authentication authentication) {
		return AuthSessionResponse.from((AuthPrincipal) authentication.getPrincipal());
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(Authentication authentication) {
		var principal = (AuthPrincipal) authentication.getPrincipal();
		authService.logout(principal.sessionId());
		return ResponseEntity.noContent().build();
	}
}
