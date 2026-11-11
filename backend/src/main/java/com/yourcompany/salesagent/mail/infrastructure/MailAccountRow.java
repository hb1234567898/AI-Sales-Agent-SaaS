package com.yourcompany.salesagent.mail.infrastructure;

public class MailAccountRow {
    private java.util.UUID organizationId;
    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID value) { organizationId = value; }
    private String host;
    public String getHost() { return host; }
    public void setHost(String value) { host = value; }
    private int port;
    public int getPort() { return port; }
    public void setPort(int value) { port = value; }
    private String username;
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    private String encryptedPassword;
    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String value) { encryptedPassword = value; }
    private boolean ssl;
    public boolean isSsl() { return ssl; }
    public void setSsl(boolean value) { ssl = value; }
    private String folder;
    public String getFolder() { return folder; }
    public void setFolder(String value) { folder = value; }
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    private long version;
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    private long uidValidity;
    public long getUidValidity() { return uidValidity; }
    public void setUidValidity(long value) { uidValidity = value; }
    private long lastUid;
    public long getLastUid() { return lastUid; }
    public void setLastUid(long value) { lastUid = value; }
    private java.time.Instant lastSyncAt;
    public java.time.Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(java.time.Instant value) { lastSyncAt = value; }
    private String lastError;
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
    private java.util.UUID leaseToken;
    public java.util.UUID getLeaseToken() { return leaseToken; }
    public void setLeaseToken(java.util.UUID value) { leaseToken = value; }
    private java.time.Instant leaseUntil;
    public java.time.Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(java.time.Instant value) { leaseUntil = value; }
}
