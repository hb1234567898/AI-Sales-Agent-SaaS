package com.yourcompany.salesagent.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.yourcompany.salesagent.auth.api.LoginRequest;
import com.yourcompany.salesagent.auth.api.PasswordPublicKeyResponse;
import com.yourcompany.salesagent.auth.application.AuthProperties;
import com.yourcompany.salesagent.shared.security.Base64KeyDecoder;

@Service
public class PasswordTransportService {

	private static final String ALGORITHM = "RSA-OAEP-256";
	private static final OAEPParameterSpec OAEP_SHA_256 = new OAEPParameterSpec(
			"SHA-256",
			"MGF1",
			MGF1ParameterSpec.SHA256,
			PSource.PSpecified.DEFAULT);

	private final AuthProperties properties;

	public PasswordTransportService(AuthProperties properties) {
		this.properties = properties;
	}

	public PasswordPublicKeyResponse publicKey() {
		if (!isEnabled()) {
			return new PasswordPublicKeyResponse(false, null, ALGORITHM, null);
		}
		try {
			return new PasswordPublicKeyResponse(
					true,
					keyId(),
					ALGORITHM,
					Base64.getEncoder().encodeToString(publicKeyBytes()));
		}
		catch (IllegalArgumentException exception) {
			throw new PasswordTransportException("登录密码加密公钥配置无效", exception);
		}
	}

	public String resolvePassword(LoginRequest request) {
		if (StringUtils.hasText(request.passwordCiphertext())) {
			return decrypt(request.passwordCiphertext(), request.passwordKeyId());
		}
		if (StringUtils.hasText(request.password())) {
			return request.password();
		}
		throw new PasswordTransportException("请输入密码");
	}

	private String decrypt(String ciphertext, String keyId) {
		if (!isEnabled()) {
			throw new PasswordTransportException("服务器尚未启用登录密码加密");
		}
		if (!keyId().equals(keyId)) {
			throw new PasswordTransportException("登录密码加密密钥版本不匹配，请刷新页面后重试");
		}
		try {
			var cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
			cipher.init(Cipher.DECRYPT_MODE, privateKey(), OAEP_SHA_256);
			return new String(cipher.doFinal(Base64.getUrlDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw new PasswordTransportException("登录密码解密失败，请刷新页面后重试", exception);
		}
	}

	private boolean isEnabled() {
		return StringUtils.hasText(properties.passwordEncryptionPublicKey())
				&& StringUtils.hasText(properties.passwordEncryptionPrivateKey());
	}

	private String keyId() {
		return StringUtils.hasText(properties.passwordEncryptionKeyId())
				? properties.passwordEncryptionKeyId().strip()
				: "default";
	}

	private PrivateKey privateKey() throws GeneralSecurityException {
		var keySpec = new PKCS8EncodedKeySpec(privateKeyBytes());
		return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
	}

	private byte[] publicKeyBytes() {
		return decodeConfiguredKey(
				properties.passwordEncryptionPublicKey(),
				"-----BEGIN PUBLIC KEY-----",
				"-----END PUBLIC KEY-----");
	}

	private byte[] privateKeyBytes() {
		return decodeConfiguredKey(
				properties.passwordEncryptionPrivateKey(),
				"-----BEGIN PRIVATE KEY-----",
				"-----END PRIVATE KEY-----");
	}

	private static byte[] decodeConfiguredKey(String value, String beginMarker, String endMarker) {
		var normalized = normalizeKeyText(value);
		if (normalized.contains(beginMarker)) {
			return Base64KeyDecoder.decode(stripPem(normalized, beginMarker, endMarker));
		}

		var decoded = Base64KeyDecoder.decode(normalized);
		var decodedText = new String(decoded, StandardCharsets.UTF_8);
		if (decodedText.contains(beginMarker)) {
			return Base64KeyDecoder.decode(stripPem(decodedText, beginMarker, endMarker));
		}
		return decoded;
	}

	private static String stripPem(String value, String beginMarker, String endMarker) {
		return normalizeKeyText(value)
				.replace(beginMarker, "")
				.replace(endMarker, "")
				.replaceAll("\\s+", "");
	}

	private static String normalizeKeyText(String value) {
		return value.strip()
				.replace("\\n", "\n");
	}
}
