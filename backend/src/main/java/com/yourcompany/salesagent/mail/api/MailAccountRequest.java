package com.yourcompany.salesagent.mail.api;
import jakarta.validation.constraints.*;
public record MailAccountRequest(@NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9.\\-]{0,253}") String host,
    @Min(1) @Max(65535) int port, @NotBlank @Email @Size(max=320) String username,
    @Size(max=500) String password, boolean ssl, @NotBlank @Size(max=120) String folder,
    boolean enabled, @Min(-1) long version) {}
