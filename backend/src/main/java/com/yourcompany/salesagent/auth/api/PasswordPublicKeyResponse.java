package com.yourcompany.salesagent.auth.api;

public record PasswordPublicKeyResponse(
		boolean enabled,
		String keyId,
		String algorithm,
		String publicKey) {
}
