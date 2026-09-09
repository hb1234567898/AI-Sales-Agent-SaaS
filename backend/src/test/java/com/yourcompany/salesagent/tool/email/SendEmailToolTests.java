package com.yourcompany.salesagent.tool.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SendEmailToolTests {

	@Test
	void sendsEmailFromNestedPayloadRecipient() {
		var mailSender = mock(JavaMailSender.class);
		var tool = new SendEmailTool(mailSender, "smtp.example.test", "", "notice@example.test");
		var actionRequestId = UUID.randomUUID();
		var context = new ToolExecutionContext(
				UUID.randomUUID(),
				actionRequestId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"send-email:" + actionRequestId,
				1);
		var message = ArgumentCaptor.forClass(SimpleMailMessage.class);

		var result = tool.execute(context, Map.of(
				"email", Map.of("to", "hecheng@example.test"),
				"subject", "跟进提醒：宁波海天机械",
				"body", "您好，请查收报价。"));

		assertThat(result.success()).isTrue();
		assertThat(result.externalOperationId()).isEqualTo(context.idempotencyKey());
		verify(mailSender).send(message.capture());
		assertThat(message.getValue().getFrom()).isEqualTo("notice@example.test");
		assertThat(message.getValue().getTo()).containsExactly("hecheng@example.test");
		assertThat(message.getValue().getSubject()).isEqualTo("跟进提醒：宁波海天机械");
		assertThat(message.getValue().getText()).isEqualTo("您好，请查收报价。");
	}

	@Test
	void failsWithoutConfiguredSmtp() {
		var tool = new SendEmailTool(mock(JavaMailSender.class), "localhost", "", "");
		var context = new ToolExecutionContext(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"send-email:test",
				1);

		var result = tool.execute(context, Map.of("to", "hecheng@example.test"));

		assertThat(result.success()).isFalse();
		assertThat(result.message()).contains("SMTP 未配置");
	}
}
