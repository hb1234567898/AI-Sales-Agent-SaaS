package com.yourcompany.salesagent.mail.api;
import jakarta.validation.constraints.Min;
public record MailVersionRequest(@Min(0) long version) {}
