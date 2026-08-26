package com.yourcompany.salesagent.auth.api;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.auth.application.AuthProperties;
import com.yourcompany.salesagent.auth.application.AuthService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final AuthProperties properties;

	public AuthController(AuthService authService, AuthProperties properties) {
		this.authService = authService;
		this.properties = properties;
	}

	@GetMapping("/csrf")
	CsrfTokenResponse csrf(CsrfToken token) {
		return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
	}

	@PostMapping("/login")
	ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		var result = authService.login(
				request.email(),
				request.password(),
				request.rememberMe(),
				servletRequest.getHeader(HttpHeaders.USER_AGENT),
				servletRequest.getRemoteAddr());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, sessionCookie(result.rawToken(), result.cookieMaxAge()).toString())
				.body(result.session());
	}

	@GetMapping("/session")
	AuthSessionResponse session(Authentication authentication) {
		return AuthSessionResponse.from((AuthPrincipal) authentication.getPrincipal());
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(Authentication authentication) {
		var principal = (AuthPrincipal) authentication.getPrincipal();
		authService.logout(principal.sessionId());
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString())
				.build();
	}

	private ResponseCookie sessionCookie(String token, Duration maxAge) {
		return ResponseCookie.from(properties.cookieName(), token)
				.httpOnly(true)
				.secure(properties.cookieSecure())
				.sameSite("Lax")
				.path("/")
				.maxAge(maxAge)
				.build();
	}
}
