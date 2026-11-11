package com.yourcompany.salesagent.followup.api;

import java.time.Instant;
import jakarta.validation.constraints.*;

public record FollowUpSnoozeRequest(@NotNull Instant dueAt, @Size(max = 2000) String reason, @NotNull @Min(0) Long version) {
}
