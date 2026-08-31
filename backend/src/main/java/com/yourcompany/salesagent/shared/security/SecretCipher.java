package com.yourcompany.salesagent.shared.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecretCipher {

	private static final String PREFIX = "v1.";
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH_BITS = 128;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final SecretEncryptionProperties properties;

	public SecretCipher(SecretEncryptionProperties properties) {
		this.properties = properties;
	}

	public boolean isConfigured() {
		if (!StringUtils.hasText(properties.encryptionKey())) return false;
		try {
			return decodeKey().length == 32;
		}
		catch (IllegalArgumentException exception) {
			return false;
		}
	}

	public String encrypt(UUID organizationId, String plaintext) {
		if (!StringUtils.hasText(plaintext)) {
			throw new SecretEncryptionException("API Key 不能为空");
		}
		try {
			var iv = new byte[IV_LENGTH];
			SECURE_RANDOM.nextBytes(iv);
			var cipher = cipher(Cipher.ENCRYPT_MODE, organizationId, iv);
			var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			var payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
			return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
		}
		catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw new SecretEncryptionException("无法加密模型 API Key，请检查服务器 APP_ENCRYPTION_KEY", exception);
		}
	}

	public String decrypt(UUID organizationId, String ciphertext) {
		if (!StringUtils.hasText(ciphertext) || !ciphertext.startsWith(PREFIX)) {
			throw new SecretEncryptionException("模型 API Key 密文格式无效");
		}
		try {
			var payload = Base64.getUrlDecoder().decode(ciphertext.substring(PREFIX.length()));
			if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("密文长度无效");
			var iv = new byte[IV_LENGTH];
			var encrypted = new byte[payload.length - IV_LENGTH];
			System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
			System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
			var cipher = cipher(Cipher.DECRYPT_MODE, organizationId, iv);
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw new SecretEncryptionException("无法解密模型 API Key，请检查服务器主密钥是否发生变化", exception);
		}
	}

	private Cipher cipher(int mode, UUID organizationId, byte[] iv) throws GeneralSecurityException {
		var key = decodeKey();
		if (key.length != 32) {
			throw new SecretEncryptionException("APP_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节密钥");
		}
		var cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
		cipher.updateAAD(organizationId.toString().getBytes(StandardCharsets.UTF_8));
		return cipher;
	}

	private byte[] decodeKey() {
		if (!StringUtils.hasText(properties.encryptionKey())) {
			throw new SecretEncryptionException("服务器尚未配置 APP_ENCRYPTION_KEY");
		}
		return Base64KeyDecoder.decode(properties.encryptionKey());
	}
}

