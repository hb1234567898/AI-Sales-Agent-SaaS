package com.yourcompany.salesagent.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.yourcompany.salesagent.auth.application.AuthProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtTokenService {

	private static final String HEADER = Base64.getUrlEncoder().withoutPadding()
			.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
	private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

	private final AuthProperties properties;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public JwtTokenService(AuthProperties properties, ObjectMapper objectMapper, Clock clock) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	public IssuedToken issueAccessToken(AuthPrincipal principal) {
		var issuedAt = clock.instant();
		var configuredExpiry = issuedAt.plus(properties.accessTokenDuration());
		var expiresAt = configuredExpiry.isBefore(principal.expiresAt())
				? configuredExpiry
				: principal.expiresAt();
		var payload = objectMapper.createObjectNode()
				.put("iss", properties.jwtIssuer())
				.put("sub", principal.userId().toString())
				.put("sid", principal.sessionId().toString())
				.put("oid", principal.organizationId().toString())
				.put("mid", principal.memberId().toString())
				.put("email", principal.email())
				.put("name", principal.displayName())
				.put("org", principal.organizationName())
				.put("role", principal.role())
				.put("session_exp", principal.expiresAt().getEpochSecond())
				.put("typ", "access")
				.put("iat", issuedAt.getEpochSecond())
				.put("exp", expiresAt.getEpochSecond())
				.put("jti", UUID.randomUUID().toString());
		return new IssuedToken(sign(payload), expiresAt);
	}

	public IssuedToken issueRefreshToken(UUID sessionId, UUID userId, Instant expiresAt) {
		var issuedAt = clock.instant();
		var payload = objectMapper.createObjectNode()
				.put("iss", properties.jwtIssuer())
				.put("sub", userId.toString())
				.put("sid", sessionId.toString())
				.put("typ", "refresh")
				.put("iat", issuedAt.getEpochSecond())
				.put("exp", expiresAt.getEpochSecond())
				.put("jti", UUID.randomUUID().toString());
		return new IssuedToken(sign(payload), expiresAt);
	}

	public AuthPrincipal parseAccessToken(String token) {
		var payload = verify(token, "access");
		try {
			return new AuthPrincipal(
					UUID.fromString(required(payload, "sid")),
					UUID.fromString(required(payload, "sub")),
					UUID.fromString(required(payload, "oid")),
					UUID.fromString(required(payload, "mid")),
					required(payload, "email"),
					required(payload, "name"),
					required(payload, "org"),
					required(payload, "role"),
					Instant.ofEpochSecond(requiredLong(payload, "session_exp")));
		}
		catch (IllegalArgumentException exception) {
			throw new JwtTokenException("Access Token 载荷无效", exception);
		}
	}

	public RefreshClaims parseRefreshToken(String token) {
		var payload = verify(token, "refresh");
		try {
			return new RefreshClaims(
					UUID.fromString(required(payload, "sid")),
					UUID.fromString(required(payload, "sub")),
					Instant.ofEpochSecond(requiredLong(payload, "exp")));
		}
		catch (IllegalArgumentException exception) {
			throw new JwtTokenException("Refresh Token 载荷无效", exception);
		}
	}

	private String sign(JsonNode payload) {
		try {
			var payloadPart = BASE64_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
			var signingInput = HEADER + "." + payloadPart;
			var signature = hmac(signingInput);
			return signingInput + "." + BASE64_ENCODER.encodeToString(signature);
		}
		catch (JwtConfigurationException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new JwtTokenException("无法签发 JWT", exception);
		}
	}

	private JsonNode verify(String token, String expectedType) {
		if (!StringUtils.hasText(token) || token.length() > 4096) {
			throw new JwtTokenException("JWT 格式无效");
		}
		var parts = token.split("\\.", -1);
		if (parts.length != 3 || !HEADER.equals(parts[0])) {
			throw new JwtTokenException("JWT 格式无效");
		}
		try {
			var expectedSignature = hmac(parts[0] + "." + parts[1]);
			var actualSignature = BASE64_DECODER.decode(parts[2]);
			if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
				throw new JwtTokenException("JWT 签名无效");
			}
			var payload = objectMapper.readTree(BASE64_DECODER.decode(parts[1]));
			if (!properties.jwtIssuer().equals(required(payload, "iss"))
					|| !expectedType.equals(required(payload, "typ"))) {
				throw new JwtTokenException("JWT 类型或签发方无效");
			}
			var expiresAt = Instant.ofEpochSecond(requiredLong(payload, "exp"));
			if (!expiresAt.isAfter(clock.instant())) {
				throw new JwtTokenException("JWT 已经过期");
			}
			return payload;
		}
		catch (JwtTokenException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new JwtTokenException("JWT 格式无效", exception);
		}
	}

	private byte[] hmac(String signingInput) {
		try {
			var mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(signingKey(), "HmacSHA256"));
			return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
		}
		catch (GeneralSecurityException exception) {
			throw new JwtConfigurationException("当前 Java 环境无法使用 HMAC-SHA256", exception);
		}
	}

	private byte[] signingKey() {
		if (!StringUtils.hasText(properties.jwtSigningKey())) {
			throw new JwtConfigurationException("服务器尚未配置 AUTH_JWT_SIGNING_KEY");
		}
		try {
			var key = Base64.getDecoder().decode(properties.jwtSigningKey().strip());
			if (key.length < 32) {
				throw new JwtConfigurationException("AUTH_JWT_SIGNING_KEY 解码后至少需要 32 字节");
			}
			return key;
		}
		catch (IllegalArgumentException exception) {
			throw new JwtConfigurationException("AUTH_JWT_SIGNING_KEY 必须使用 Base64 编码", exception);
		}
	}

	private static String required(JsonNode payload, String field) {
		var value = payload.path(field).asText("");
		if (!StringUtils.hasText(value)) throw new JwtTokenException("JWT 缺少字段 " + field);
		return value;
	}

	private static long requiredLong(JsonNode payload, String field) {
		var value = payload.path(field);
		if (!value.isIntegralNumber()) throw new JwtTokenException("JWT 缺少字段 " + field);
		return value.asLong();
	}

	public record IssuedToken(String value, Instant expiresAt) {
	}

	public record RefreshClaims(UUID sessionId, UUID userId, Instant expiresAt) {
	}
}
