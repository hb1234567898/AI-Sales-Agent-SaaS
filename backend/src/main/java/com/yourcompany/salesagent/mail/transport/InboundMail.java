package com.yourcompany.salesagent.mail.transport;
import java.time.Instant;
import java.util.List;
public record InboundMail(String messageId, String fromAddress, List<String> recipients, String subject, String body,
    List<String> attachmentNames, List<String> references, Instant occurredAt, boolean automatic) {}
