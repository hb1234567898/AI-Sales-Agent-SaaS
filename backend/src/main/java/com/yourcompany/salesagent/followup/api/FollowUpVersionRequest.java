package com.yourcompany.salesagent.followup.api;

import jakarta.validation.constraints.*;

public record FollowUpVersionRequest(@NotNull @Min(0) Long version) {
}
