package com.yourcompany.salesagent.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class Base64KeyDecoderTests {

	@Test
	void decodesBase64KeyWithoutPadding() {
		var padded = Base64.getEncoder().encodeToString("kskblzdjdzkbl".getBytes(StandardCharsets.UTF_8));
		var unpadded = padded.replace("=", "");

		assertThat(Base64KeyDecoder.decode(unpadded)).isEqualTo("kskblzdjdzkbl".getBytes(StandardCharsets.UTF_8));
		assertThat(Base64KeyDecoder.withPadding(unpadded)).endsWith("==");
	}
}
