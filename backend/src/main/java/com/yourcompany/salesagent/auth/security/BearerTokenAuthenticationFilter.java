package com.yourcompany.salesagent.auth.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.yourcompany.salesagent.auth.application.AuthService;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String FALLBACK_ACCESS_TOKEN_HEADER = "X-Sales-Agent-Access-Token";
	public static final String AUTH_TOKEN_STATUS_ATTRIBUTE = "salesAgent.authTokenStatus";
	public static final String AUTHORIZATION_HEADER_PRESENT_ATTRIBUTE = "salesAgent.authorizationHeaderPresent";
	public static final String FALLBACK_HEADER_PRESENT_ATTRIBUTE = "salesAgent.fallbackAccessTokenHeaderPresent";
	private final AuthService authService;

	public BearerTokenAuthenticationFilter(AuthService authService) {
		this.authService = authService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		request.setAttribute(AUTHORIZATION_HEADER_PRESENT_ATTRIBUTE, hasTextHeader(request, HttpHeaders.AUTHORIZATION));
		request.setAttribute(FALLBACK_HEADER_PRESENT_ATTRIBUTE, hasTextHeader(request, FALLBACK_ACCESS_TOKEN_HEADER));
		if (shouldResolveToken(SecurityContextHolder.getContext().getAuthentication())) {
			var token = readBearerToken(request);
			if (token.isEmpty()) {
				request.setAttribute(AUTH_TOKEN_STATUS_ATTRIBUTE, "missing");
			}
			else {
				var principal = authService.resolveAccessToken(token.get());
				request.setAttribute(AUTH_TOKEN_STATUS_ATTRIBUTE, principal.isPresent() ? "accepted" : "invalid");
				principal.ifPresent(value -> {
					var authority = new SimpleGrantedAuthority("ROLE_" + value.role());
					var authentication = new UsernamePasswordAuthenticationToken(value, null, List.of(authority));
					var context = SecurityContextHolder.createEmptyContext();
					context.setAuthentication(authentication);
					SecurityContextHolder.setContext(context);
				});
			}
		}
		filterChain.doFilter(request, response);
	}

	private java.util.Optional<String> readBearerToken(HttpServletRequest request) {
		var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
			var token = authorization.substring(7).strip();
			if (!token.isEmpty()) {
				return java.util.Optional.of(token);
			}
		}
		var fallbackToken = request.getHeader(FALLBACK_ACCESS_TOKEN_HEADER);
		return fallbackToken == null || fallbackToken.isBlank()
				? java.util.Optional.empty()
				: java.util.Optional.of(fallbackToken.strip());
	}

	private static boolean hasTextHeader(HttpServletRequest request, String name) {
		var value = request.getHeader(name);
		return value != null && !value.isBlank();
	}

	private static boolean shouldResolveToken(Authentication authentication) {
		return authentication == null || authentication instanceof AnonymousAuthenticationToken;
	}
}
