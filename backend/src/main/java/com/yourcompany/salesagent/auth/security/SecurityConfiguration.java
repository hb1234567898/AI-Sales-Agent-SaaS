package com.yourcompany.salesagent.auth.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	FilterRegistrationBean<BearerTokenAuthenticationFilter> bearerTokenAuthenticationFilterRegistration(
			BearerTokenAuthenticationFilter bearerTokenFilter) {
		var registration = new FilterRegistrationBean<>(bearerTokenFilter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerTokenFilter) throws Exception {
		http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/password-key", "/actuator/health", "/actuator/info").permitAll()
						.requestMatchers("/api/v1/audit-events/**", "/api/v1/admin/**").authenticated()
						.requestMatchers("/api/v1/auth/session").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.setCharacterEncoding(StandardCharsets.UTF_8.name());
					response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
					response.setHeader("X-Sales-Agent-Auth-Token", String.valueOf(request.getAttribute(BearerTokenAuthenticationFilter.AUTH_TOKEN_STATUS_ATTRIBUTE)));
					response.setHeader("X-Sales-Agent-Auth-Authorization", String.valueOf(request.getAttribute(BearerTokenAuthenticationFilter.AUTHORIZATION_HEADER_PRESENT_ATTRIBUTE)));
					response.setHeader("X-Sales-Agent-Auth-Fallback", String.valueOf(request.getAttribute(BearerTokenAuthenticationFilter.FALLBACK_HEADER_PRESENT_ATTRIBUTE)));
					response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"登录状态已失效，请重新登录\"}");
				}))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.addFilterBefore(bearerTokenFilter, AnonymousAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${app.security.cors.allowed-origin-patterns}") List<String> allowedOriginPatterns) {
		var configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(allowedOriginPatterns);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Sales-Agent-Access-Token"));
		configuration.setExposedHeaders(List.of(
				"X-Sales-Agent-Auth-Token",
				"X-Sales-Agent-Auth-Authorization",
				"X-Sales-Agent-Auth-Fallback"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);

		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		source.registerCorsConfiguration("/actuator/**", configuration);
		return source;
	}
}
