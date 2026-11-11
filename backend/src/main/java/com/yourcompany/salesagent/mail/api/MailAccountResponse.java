package com.yourcompany.salesagent.mail.api;
import java.time.Instant;
public record MailAccountResponse(String host, int port, String username, boolean ssl, String folder,
    boolean enabled, boolean passwordConfigured, long version, Instant lastSyncAt, String lastError, boolean syncing) {}
