package com.yourcompany.salesagent.auth.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.yourcompany.salesagent.auth.application.AuthProperties;
import com.yourcompany.salesagent.auth.application.AuthService;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

	private final AuthService authService;
	private final AuthProperties properties;

	public SessionAuthenticationFilter(AuthService authService, AuthProperties properties) {
		this.authService = authService;
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			readSessionCookie(request).flatMap(authService::resolveSession).ifPresent(principal -> {
				var authority = new SimpleGrantedAuthority("ROLE_" + principal.role());
				var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
				var context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
			});
		}
		filterChain.doFilter(request, response);
	}

	private java.util.Optional<String> readSessionCookie(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return java.util.Optional.empty();
		}
		return Arrays.stream(request.getCookies())
				.filter(cookie -> properties.cookieName().equals(cookie.getName()))
				.map(Cookie::getValue)
				.filter(value -> !value.isBlank())
				.findFirst();
	}
}
