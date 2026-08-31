package com.yourcompany.salesagent.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.junit.jupiter.api.Test;

import com.yourcompany.salesagent.auth.api.LoginRequest;
import com.yourcompany.salesagent.auth.application.AuthProperties;

class PasswordTransportServiceTests {

	private static final OAEPParameterSpec OAEP_SHA_256 = new OAEPParameterSpec(
			"SHA-256",
			"MGF1",
			MGF1ParameterSpec.SHA256,
			PSource.PSpecified.DEFAULT);

	@Test
	void decryptsEncryptedLoginPassword() throws Exception {
		var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		var keyPair = keyPairGenerator.generateKeyPair();
		var service = service(
				Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
				Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
		var cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), OAEP_SHA_256);

		var encrypted = Base64.getUrlEncoder().withoutPadding().encodeToString(
				cipher.doFinal("Demo@123456".getBytes(StandardCharsets.UTF_8)));
		var request = new LoginRequest("chen.mo@demo.local", null, encrypted, "login-key-1", true);

		assertThat(service.resolvePassword(request)).isEqualTo("Demo@123456");
		assertThat(service.publicKey().enabled()).isTrue();
		assertThat(service.publicKey().publicKey()).isNotBlank();
	}

	@Test
	void acceptsBase64EncodedPemWithoutPaddingFromPanelEnvironment() throws Exception {
		var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		var keyPair = keyPairGenerator.generateKeyPair();
		var service = service(
				withoutPadding(Base64.getEncoder().encodeToString(pem("PUBLIC KEY", keyPair.getPublic().getEncoded()).getBytes(StandardCharsets.UTF_8))),
				withoutPadding(Base64.getEncoder().encodeToString(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()).getBytes(StandardCharsets.UTF_8))));
		var cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), OAEP_SHA_256);

		var encrypted = Base64.getUrlEncoder().withoutPadding().encodeToString(
				cipher.doFinal("Demo@123456".getBytes(StandardCharsets.UTF_8)));

		assertThat(service.publicKey().publicKey()).doesNotStartWith("LS0t");
		assertThat(service.resolvePassword(
				new LoginRequest("chen.mo@demo.local", null, encrypted, "login-key-1", true)))
						.isEqualTo("Demo@123456");
	}

	private static PasswordTransportService service(String publicKey, String privateKey) {
		return new PasswordTransportService(new AuthProperties(
				Duration.ofMinutes(15),
				Duration.ofHours(12),
				Duration.ofDays(30),
				"ai-sales-agent",
				"unused",
				"login-key-1",
				publicKey,
				privateKey));
	}

	private static String pem(String type, byte[] bytes) {
		return "-----BEGIN " + type + "-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(bytes)
				+ "\n-----END " + type + "-----\n";
	}

	private static String withoutPadding(String value) {
		return value.replace("=", "");
	}
}
