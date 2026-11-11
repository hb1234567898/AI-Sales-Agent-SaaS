package com.yourcompany.salesagent.followup.api;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.*;

public record FollowUpUpsertRequest(
        @NotNull UUID customerId,
        @NotNull UUID ownerMemberId,
        @NotBlank @Size(max = 200) String title,
        @NotNull Instant dueAt,
        @Min(0) @Max(100) int priority,
        @Size(max = 2000) String reason,
        @NotBlank @Pattern(regexp = "CALL|EMAIL|MEETING|OTHER") String actionType,
        @Min(0) Long version) {
}
