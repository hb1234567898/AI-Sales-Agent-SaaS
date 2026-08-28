package com.yourcompany.salesagent.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.secrets")
public record SecretEncryptionProperties(String encryptionKey) {
}

