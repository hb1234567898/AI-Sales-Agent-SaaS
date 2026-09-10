package com.yourcompany.salesagent.tool.email;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;
import com.yourcompany.salesagent.tool.domain.ToolResult;
import com.yourcompany.salesagent.tool.domain.ToolRisk;
import com.yourcompany.salesagent.tool.spi.AgentTool;
import com.yourcompany.salesagent.tool.spi.ToolDescriptor;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 真实发送邮件。HIGH 风险，必须由人工审批后才能执行（策略见设计文档 8.2 节）。
 * 未配置 SMTP 时主动返回失败而非崩溃；发送失败不重试，避免重复触达客户。
 */
@Component
public class SendEmailTool implements AgentTool {

	private final JavaMailSender mailSender;
	private final EmailConfigurationService configurationService;

	public SendEmailTool(
			JavaMailSender mailSender,
			EmailConfigurationService configurationService) {
		this.mailSender = mailSender;
		this.configurationService = configurationService;
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
		try {
			var message = new SimpleMailMessage();
			message.setFrom(configuration.fromAddress());
			message.setTo(recipients);
			message.setSubject(subject == null ? "(无主题)" : subject);
			message.setText(body == null ? "" : body);
			sender(configuration).send(message);
			return ToolResult.success(context.idempotencyKey(), "邮件已发送至 " + String.join(", ", recipients),
					Map.of("to", String.join(", ", recipients), "subject", subject == null ? "(无主题)" : subject));
		}
		catch (MailException e) {
			// 发送失败直接标记失败，交由人工处理；不盲目重试，避免重复触达客户。
			return ToolResult.failure("邮件发送失败: " + e.getMessage());
		}
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
