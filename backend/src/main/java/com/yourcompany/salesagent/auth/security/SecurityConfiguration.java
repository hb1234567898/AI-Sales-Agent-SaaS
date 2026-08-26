package com.yourcompany.salesagent.auth.security;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * 该过滤器只允许由 Spring Security 管理。关闭 Servlet 容器自动注册，
	 * 避免它在 SecurityContextHolderFilter 之前执行后又被空上下文覆盖。
	 */
	@Bean
	FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthenticationFilterRegistration(
			SessionAuthenticationFilter sessionFilter) {
		var registration = new FilterRegistrationBean<>(sessionFilter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SessionAuthenticationFilter sessionFilter) throws Exception {
		var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookiePath("/");

		http
				.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/actuator/health", "/actuator/info").permitAll()
						.requestMatchers("/api/v1/auth/session").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.setCharacterEncoding(StandardCharsets.UTF_8.name());
					response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
					response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"登录状态已失效，请重新登录\"}");
				}))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class);

		return http.build();
	}
}
