package com.yourcompany.salesagent.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.auth.application.AuthService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.auth.security.PasswordTransportService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final PasswordTransportService passwordTransportService;

	public AuthController(AuthService authService, PasswordTransportService passwordTransportService) {
		this.authService = authService;
		this.passwordTransportService = passwordTransportService;
	}

	@GetMapping("/password-key")
	PasswordPublicKeyResponse passwordKey() {
		return passwordTransportService.publicKey();
	}

	@PostMapping("/login")
	AuthTokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		var result = authService.login(
				request.email(),
				passwordTransportService.resolvePassword(request),
				request.rememberMe(),
				servletRequest.getHeader(HttpHeaders.USER_AGENT),
				servletRequest.getRemoteAddr());
		return AuthTokenResponse.from(result);
	}

	@PostMapping("/refresh")
	AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return AuthTokenResponse.from(authService.refresh(request.refreshToken()));
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
