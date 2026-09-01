package com.yourcompany.salesagent.audit.application;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import com.yourcompany.salesagent.auth.security.AuthPrincipal;

@Aspect
@Component
public class AuditLogAspect {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogAspect.class);
	private static final Pattern SAFE_IP_PATTERN = Pattern.compile("[0-9a-fA-F:.]+");

	private final AuditLogService auditLogService;
	private final Clock clock;

	public AuditLogAspect(AuditLogService auditLogService, Clock clock) {
		this.auditLogService = auditLogService;
		this.clock = clock;
	}

	@Around("within(@org.springframework.web.bind.annotation.RestController *) && execution(public * com.yourcompany.salesagent..api..*(..))")
	public Object recordWriteOperation(ProceedingJoinPoint joinPoint) throws Throwable {
		var request = currentRequest();
		if (request == null || !shouldAudit(request)) {
			return joinPoint.proceed();
		}

		var startedAt = System.nanoTime();
		Object result = null;
		Throwable failure = null;
		try {
			result = joinPoint.proceed();
			return result;
		}
		catch (Throwable exception) {
			failure = exception;
			throw exception;
		}
		finally {
			recordAuditEvent(joinPoint, request, result, failure, elapsedMillis(startedAt));
		}
	}

	private void recordAuditEvent(
			ProceedingJoinPoint joinPoint,
			HttpServletRequest request,
			Object returnValue,
			Throwable failure,
			long durationMs) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
			return;
		}

		try {
			var target = resolveTarget(request, returnValue, principal);
			auditLogService.record(new AuditLogCommand(
					principal.organizationId(),
					principal.memberId(),
					principal.email(),
					action(request),
					target.type(),
					target.id(),
					result(failure),
					clientIp(request),
					truncate(request.getHeader("User-Agent"), 1000),
					truncate(request.getHeader("X-Request-Id"), 160),
					metadata(joinPoint, request, returnValue, failure, durationMs),
					clock.instant()));
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Failed to persist audit event for {} {}", request.getMethod(), request.getRequestURI(), exception);
		}
	}

	private static boolean shouldAudit(HttpServletRequest request) {
		var method = request.getMethod();
		var path = request.getRequestURI();
		return path.startsWith("/api/v1/")
				&& !path.equals("/api/v1/auth/login")
				&& !path.equals("/api/v1/auth/refresh")
				&& ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method));
	}

	private static String action(HttpServletRequest request) {
		return "HTTP_" + request.getMethod();
	}

	private static String result(Throwable failure) {
		if (failure == null) return "SUCCEEDED";
		return failure instanceof AccessDeniedException ? "DENIED" : "FAILED";
	}

	private static Map<String, Object> metadata(
			ProceedingJoinPoint joinPoint,
			HttpServletRequest request,
			Object returnValue,
			Throwable failure,
			long durationMs) {
		var metadata = new LinkedHashMap<String, Object>();
		metadata.put("method", request.getMethod());
		metadata.put("path", request.getRequestURI());
		metadata.put("routePattern", routePattern(request));
		if (request.getQueryString() != null) {
			metadata.put("queryString", request.getQueryString());
		}
		metadata.put("handler", joinPoint.getSignature().toShortString());
		metadata.put("durationMs", durationMs);
		metadata.put("status", statusCode(returnValue, failure));
		if (failure != null) {
			metadata.put("errorType", failure.getClass().getSimpleName());
			metadata.put("errorMessage", truncate(failure.getMessage(), 300));
		}
		return metadata;
	}

	private static Target resolveTarget(HttpServletRequest request, Object returnValue, AuthPrincipal principal) {
		var variables = pathVariables(request);
		var returnedId = extractReturnedId(returnValue);
		var path = request.getRequestURI();
		if (path.startsWith("/api/v1/admin/members")) {
			return new Target("TEAM_MEMBER", firstNonBlank(variables.get("memberId"), returnedId, "N/A"));
		}
		if (path.startsWith("/api/v1/admin/team")) {
			return new Target("TEAM", principal.organizationId().toString());
		}
		if (path.startsWith("/api/v1/ai/model/test")) {
			return new Target("AI_MODEL_TEST", principal.organizationId().toString());
		}
		if (path.startsWith("/api/v1/ai/model")) {
			return new Target("AI_MODEL", principal.organizationId().toString());
		}
		if (path.equals("/api/v1/auth/logout")) {
			return new Target("AUTH_SESSION", principal.sessionId().toString());
		}
		if (path.endsWith("/chat-import")) {
			return new Target("CHAT_IMPORT", firstNonBlank(returnedId, variables.get("customerId"), "N/A"));
		}
		if (path.contains("/analysis/") || path.endsWith("/analysis")) {
			return new Target("CHAT_ANALYSIS", firstNonBlank(
					variables.get("analysisId"), variables.get("interactionId"), returnedId, variables.get("customerId"), "N/A"));
		}
		if (path.contains("/interactions")) {
			return new Target("INTERACTION", firstNonBlank(
					variables.get("interactionId"), returnedId, variables.get("customerId"), "N/A"));
		}
		if (path.startsWith("/api/v1/customers")) {
			return new Target("CUSTOMER", firstNonBlank(returnedId, variables.get("customerId"), "N/A"));
		}
		return new Target("API", firstNonBlank(returnedId, routePattern(request), path));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> pathVariables(HttpServletRequest request) {
		var variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		return variables instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
	}

	private static String routePattern(HttpServletRequest request) {
		var pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return pattern == null ? request.getRequestURI() : pattern.toString();
	}

	private static String extractReturnedId(Object returnValue) {
		var body = returnValue instanceof ResponseEntity<?> response ? response.getBody() : returnValue;
		if (body == null) return null;
		try {
			Method idMethod = body.getClass().getMethod("id");
			var id = idMethod.invoke(body);
			return id == null ? null : id.toString();
		}
		catch (ReflectiveOperationException exception) {
			return null;
		}
	}

	private static int statusCode(Object returnValue, Throwable failure) {
		if (failure != null) {
			return failure instanceof AccessDeniedException ? 403 : 500;
		}
		if (returnValue instanceof ResponseEntity<?> response) {
			return response.getStatusCode().value();
		}
		return 200;
	}

	private static String clientIp(HttpServletRequest request) {
		var forwardedFor = request.getHeader("X-Forwarded-For");
		var ip = forwardedFor == null || forwardedFor.isBlank()
				? request.getRemoteAddr()
				: forwardedFor.split(",", 2)[0].trim();
		ip = ip == null ? null : ip.strip();
		return ip != null && SAFE_IP_PATTERN.matcher(ip).matches() ? ip : null;
	}

	private static String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) return value;
		return value.substring(0, maxLength);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) return value;
		}
		return "N/A";
	}

	private static long elapsedMillis(long startedAt) {
		return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
	}

	private static HttpServletRequest currentRequest() {
		var attributes = RequestContextHolder.getRequestAttributes();
		return attributes instanceof ServletRequestAttributes servletAttributes
				? servletAttributes.getRequest()
				: null;
	}

	private record Target(String type, String id) {
	}
}
