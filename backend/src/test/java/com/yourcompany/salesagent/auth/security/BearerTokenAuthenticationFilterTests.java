package com.yourcompany.salesagent.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.yourcompany.salesagent.auth.application.AuthService;

class BearerTokenAuthenticationFilterTests {

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void authenticatesWithProxySafeFallbackHeaderWhenAuthorizationIsUnavailable() throws Exception {
		var authService = mock(AuthService.class);
		var principal = principal();
		when(authService.resolveAccessToken("access.jwt")).thenReturn(Optional.of(principal));
		var filter = new BearerTokenAuthenticationFilter(authService);
		var request = new MockHttpServletRequest("PUT", "/api/v1/ai/model");
		request.addHeader("X-Sales-Agent-Access-Token", "access.jwt");

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
	}

	private static AuthPrincipal principal() {
		return new AuthPrincipal(
				UUID.fromString("30000000-0000-0000-0000-000000000001"),
				UUID.fromString("10000000-0000-0000-0000-000000000001"),
				UUID.fromString("00000000-0000-0000-0000-000000000001"),
				UUID.fromString("20000000-0000-0000-0000-000000000001"),
				"chen.mo@demo.local",
				"陈默",
				"演示销售团队",
				"SALES",
				Instant.parse("2026-09-25T02:00:00Z"));
	}
}
