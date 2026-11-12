package com.yourcompany.salesagent.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import com.yourcompany.salesagent.auth.application.AuthService;
import com.yourcompany.salesagent.auth.security.PasswordTransportService;

@RestController
@RequestMapping("/api/public/auth")
public class PublicAuthController {
    private final AuthService authService;
    private final PasswordTransportService passwordTransportService;

    public PublicAuthController(AuthService authService, PasswordTransportService passwordTransportService) {
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

}
