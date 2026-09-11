package com.yourcompany.salesagent.tool.email;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.yourcompany.salesagent.file.application.FileStorageException;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.file.domain.UploadedFile;
import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;
import com.yourcompany.salesagent.tool.domain.ToolResult;
import com.yourcompany.salesagent.tool.domain.ToolRisk;
import com.yourcompany.salesagent.tool.spi.AgentTool;
import com.yourcompany.salesagent.tool.spi.ToolDescriptor;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 真实发送邮件。HIGH 风险，必须由人工审批后才能执行（策略见设计文档 8.2 节）。
 * 未配置 SMTP 时主动返回失败而非崩溃；发送失败不重试，避免重复触达客户。
 */
@Component
public class SendEmailTool implements AgentTool {

	private final JavaMailSender mailSender;
	private final EmailConfigurationService configurationService;
	private final FileStorageService fileStorageService;

	public SendEmailTool(
			JavaMailSender mailSender,
			EmailConfigurationService configurationService,
			FileStorageService fileStorageService) {
		this.mailSender = mailSender;
		this.configurationService = configurationService;
		this.fileStorageService = fileStorageService;
	}

	@Override
	public ToolDescriptor descriptor() {
		return new ToolDescriptor("email.send", "v1", ToolRisk.HIGH, false, Duration.ofSeconds(30), 0);
	}

	@Override
	public ToolResult execute(ToolExecutionContext context, Map<String, Object> payload) {
		EmailRuntimeConfiguration configuration;
		try {
			configuration = configurationService.requireRuntimeConfiguration(context.organizationId());
		}
		catch (EmailConfigurationException exception) {
			return ToolResult.failure(exception.getMessage());
		}
		var to = firstText(asString(payload.get("to")), nestedEmailField(payload, "to"));
		var subject = asString(payload.get("subject"));
		var body = asString(payload.get("body"));
		if (to == null || to.isBlank()) {
			return ToolResult.failure("缺少收件人(to)，无法发送邮件");
		}
		var recipients = recipients(to);
		if (recipients.length == 0) {
			return ToolResult.failure("收件人(to)格式不正确，无法发送邮件");
		}
		var attachments = resolveAttachments(context, payload);
		if (attachments == null) {
			return ToolResult.failure("邮件需要附件，但没有找到可发送的上传文件，请先在客户档案上传文件后再审批");
		}
		try {
			var mail = sender(configuration);
			if (attachments.isEmpty()) {
				var message = new SimpleMailMessage();
				message.setFrom(configuration.fromAddress());
				message.setTo(recipients);
				message.setSubject(subject == null ? "(无主题)" : subject);
				message.setText(body == null ? "" : body);
				mail.send(message);
			}
			else {
				var message = mail.createMimeMessage();
				var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
				helper.setFrom(configuration.fromAddress());
				helper.setTo(recipients);
				helper.setSubject(subject == null ? "(无主题)" : subject);
				helper.setText(body == null ? "" : body, false);
				for (var attachment : attachments) {
					helper.addAttachment(attachment.getOriginalFilename(), new ByteArrayResource(attachment.getContent()),
							attachment.getContentType());
				}
				mail.send(message);
			}
			var attachmentPreview = attachments.isEmpty() ? List.<Map<String, Object>>of() : fileStorageService.preview(attachments);
			return ToolResult.success(context.idempotencyKey(), "邮件已发送至 " + String.join(", ", recipients),
					Map.of(
							"to", String.join(", ", recipients),
							"subject", subject == null ? "(无主题)" : subject,
							"attachments", attachmentPreview));
		}
		catch (MailException | jakarta.mail.MessagingException e) {
			// 发送失败直接标记失败，交由人工处理；不盲目重试，避免重复触达客户。
			return ToolResult.failure("邮件发送失败: " + e.getMessage());
		}
	}

	private List<UploadedFile> resolveAttachments(ToolExecutionContext context, Map<String, Object> payload) {
		var attachmentIds = attachmentIds(payload);
		var required = Boolean.TRUE.equals(payload.get("attachmentRequired"));
		try {
			if (!attachmentIds.isEmpty()) {
				var files = new ArrayList<UploadedFile>();
				for (var attachmentId : attachmentIds) {
					files.add(fileStorageService.requireActive(context.organizationId(), attachmentId));
				}
				return files;
			}
			if (required && context.customerId() != null) {
				var files = fileStorageService.findRecentForCustomer(context.organizationId(), context.customerId(), 3);
				return files.isEmpty() ? null : files;
			}
			return required ? null : List.of();
		}
		catch (FileStorageException exception) {
			return null;
		}
	}

	private static List<UUID> attachmentIds(Map<String, Object> payload) {
		var value = firstAttachmentList(payload);
		if (!(value instanceof Iterable<?> items)) {
			return List.of();
		}
		var ids = new ArrayList<UUID>();
		for (var item : items) {
			var id = attachmentId(item);
			if (id != null) {
				ids.add(id);
			}
		}
		return ids;
	}

	private static Object firstAttachmentList(Map<String, Object> payload) {
		var value = payload.get("attachments");
		if (value instanceof Iterable<?>) {
			return value;
		}
		var email = payload.get("email");
		if (email instanceof Map<?, ?> map) {
			return map.get("attachments");
		}
		return value;
	}

	private static UUID attachmentId(Object item) {
		try {
			if (item instanceof UUID id) {
				return id;
			}
			if (item instanceof String text && !text.isBlank()) {
				return UUID.fromString(text.strip());
			}
			if (item instanceof Map<?, ?> map) {
				var id = map.get("id");
				return id == null ? null : UUID.fromString(String.valueOf(id).strip());
			}
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
		return null;
	}

	private JavaMailSender sender(EmailRuntimeConfiguration configuration) {
		if (configurationService == null) {
			return mailSender;
		}
		return configurationService.mailSender(configuration);
	}

	private static String[] recipients(String value) {
		return Arrays.stream(value.split("[,;]"))
				.map(String::strip)
				.filter(SendEmailTool::looksLikeEmail)
				.distinct()
				.toArray(String[]::new);
	}

	private static boolean looksLikeEmail(String value) {
		return value != null && value.length() <= 320 && value.contains("@") && !value.contains(" ");
	}

	@SuppressWarnings("unchecked")
	private static String nestedEmailField(Map<String, Object> payload, String field) {
		var email = payload.get("email");
		if (!(email instanceof Map<?, ?> map)) {
			return null;
		}
		return asString(((Map<String, Object>) map).get(field));
	}

	private static String firstText(String... values) {
		return Arrays.stream(values)
				.filter(Objects::nonNull)
				.map(String::strip)
				.filter(value -> !value.isBlank())
				.findFirst()
				.orElse(null);
	}

	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
