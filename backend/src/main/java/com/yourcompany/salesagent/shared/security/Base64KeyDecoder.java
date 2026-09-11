package com.yourcompany.salesagent.shared.security;

import java.util.Base64;

public final class Base64KeyDecoder {

	private Base64KeyDecoder() {
	}

	public static byte[] decode(String value) {
		return Base64.getDecoder().decode(withPadding(value));
	}

	public static String withPadding(String value) {
		var normalized = value.strip();
		var remainder = normalized.length() % 4;
		if (remainder == 0) return normalized;
		return normalized + "=".repeat(4 - remainder);
	}
}
