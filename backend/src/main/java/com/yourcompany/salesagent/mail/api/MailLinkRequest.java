package com.yourcompany.salesagent.mail.api;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record MailLinkRequest(@NotNull UUID customerId, @Min(0) long version) {}
