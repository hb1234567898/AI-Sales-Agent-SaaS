package com.yourcompany.salesagent.auth.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.yourcompany.salesagent.auth.application.AuthService;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

	private final AuthService authService;

	public BearerTokenAuthenticationFilter(AuthService authService) {
		this.authService = authService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			readBearerToken(request).flatMap(authService::resolveAccessToken).ifPresent(principal -> {
				var authority = new SimpleGrantedAuthority("ROLE_" + principal.role());
				var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
				var context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
			});
		}
		filterChain.doFilter(request, response);
	}

	private java.util.Optional<String> readBearerToken(HttpServletRequest request) {
		var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return java.util.Optional.empty();
		}
		var token = authorization.substring(7).strip();
		return token.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(token);
	}
}
