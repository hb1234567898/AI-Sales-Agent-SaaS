package com.yourcompany.salesagent.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SecretCipherTests {

	private final String encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
	private final SecretCipher cipher = new SecretCipher(new SecretEncryptionProperties(encryptionKey));

	@Test
	void encryptsAndDecryptsSecretForTheSameOrganization() {
		var organizationId = UUID.fromString("00000000-0000-0000-0000-000000000001");

		var encrypted = cipher.encrypt(organizationId, "sk-sensitive-value");

		assertThat(encrypted).startsWith("v1.").doesNotContain("sk-sensitive-value");
		assertThat(cipher.decrypt(organizationId, encrypted)).isEqualTo("sk-sensitive-value");
	}

	@Test
	void refusesToDecryptSecretForAnotherOrganization() {
		var encrypted = cipher.encrypt(
				UUID.fromString("00000000-0000-0000-0000-000000000001"),
				"sk-sensitive-value");

		assertThatThrownBy(() -> cipher.decrypt(
				UUID.fromString("00000000-0000-0000-0000-000000000002"),
				encrypted)).isInstanceOf(SecretEncryptionException.class);
	}
}
