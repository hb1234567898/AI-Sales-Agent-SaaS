package com.yourcompany.salesagent.tool.email.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmailSettingsUpdateRequest(
		@NotBlank @Size(max = 255) String host,
		@NotNull @Min(1) @Max(65535) Integer port,
		@Size(max = 320) String username,
		@Size(max = 500) String password,
		@NotBlank @Email @Size(max = 320) String fromAddress,
		Boolean smtpAuth,
		Boolean starttlsEnabled,
		Boolean starttlsRequired) {
}
