package com.yourcompany.salesagent.mail.api;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record MailReplyRequest(@NotBlank @Size(max=500) String subject, @NotBlank @Size(max=20000) String body,
    @NotNull @Size(max=10) List<@NotNull UUID> attachmentIds, @Min(0) long version) {}
