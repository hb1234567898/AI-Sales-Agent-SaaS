package com.yourcompany.salesagent.tool.email;

import java.time.Duration;
import java.util.Map;

import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;
import com.yourcompany.salesagent.tool.domain.ToolResult;
import com.yourcompany.salesagent.tool.domain.ToolRisk;
import com.yourcompany.salesagent.tool.spi.AgentTool;
import com.yourcompany.salesagent.tool.spi.ToolDescriptor;

import org.springframework.beans.factory.annotation.Value;
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
	private final String mailHost;
	private final String mailUsername;

	public SendEmailTool(
			JavaMailSender mailSender,
			@Value("${spring.mail.host:}") String mailHost,
			@Value("${spring.mail.username:}") String mailUsername) {
		this.mailSender = mailSender;
		this.mailHost = mailHost;
		this.mailUsername = mailUsername;
	}

	@Override
	public ToolDescriptor descriptor() {
		return new ToolDescriptor("email.send", "v1", ToolRisk.HIGH, false, Duration.ofSeconds(30), 0);
	}

	@Override
	public ToolResult execute(ToolExecutionContext context, Map<String, Object> payload) {
		var host = mailHost;
		var username = mailUsername;
		var unconfigured = host == null || host.isBlank()
				|| (host.equals("localhost") && (username == null || username.isBlank()));
		if (unconfigured) {
			return ToolResult.failure("SMTP 未配置：请在 .env 设置 MAIL_HOST/MAIL_USERNAME 等变量后再发送");
		}
		var to = asString(payload.get("to"));
		var subject = asString(payload.get("subject"));
		var body = asString(payload.get("body"));
		if (to == null || to.isBlank()) {
			return ToolResult.failure("缺少收件人(to)，无法发送邮件");
		}
		try {
			var message = new SimpleMailMessage();
			message.setFrom(username != null && !username.isBlank() ? username : "no-reply@local");
			message.setTo(to.split("[,;]"));
			message.setSubject(subject == null ? "(无主题)" : subject);
			message.setText(body == null ? "" : body);
			mailSender.send(message);
			return ToolResult.success("邮件已发送至 " + to, Map.of("to", to, "subject", subject));
		}
		catch (MailException e) {
			// 发送失败直接标记失败，交由人工处理；不盲目重试，避免重复触达客户。
			return ToolResult.failure("邮件发送失败: " + e.getMessage());
		}
	}

	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
