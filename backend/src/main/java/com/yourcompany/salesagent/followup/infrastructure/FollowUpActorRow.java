package com.yourcompany.salesagent.followup.infrastructure;

import java.time.Instant;
import java.util.UUID;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;

public class FollowUpActorRow {
    private UUID organizationId;
    private UUID memberId;
    private UUID userId;
    private String email;
    private String displayName;
    private String organizationName;
    private String role;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID id) { organizationId = id; }
    public UUID getMemberId() { return memberId; }
    public void setMemberId(UUID id) { memberId = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID id) { userId = id; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String value) { organizationName = value; }
    public String getRole() { return role; }
    public void setRole(String value) { role = value; }
    public AuthPrincipal principal(Instant now) {
        return new AuthPrincipal(null, userId, organizationId, memberId, email, displayName, organizationName, role, now.plusSeconds(3600));
    }
}
