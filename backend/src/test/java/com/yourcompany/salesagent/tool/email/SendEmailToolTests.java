package com.yourcompany.salesagent.tool.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SendEmailToolTests {

	@Test
	void sendsEmailFromNestedPayloadRecipient() {
		var mailSender = mock(JavaMailSender.class);
		var configurationService = mock(EmailConfigurationService.class);
		var fileStorageService = mock(FileStorageService.class);
		var tool = new SendEmailTool(mailSender, configurationService, fileStorageService);
		var actionRequestId = UUID.randomUUID();
		var context = new ToolExecutionContext(
				UUID.randomUUID(),
				actionRequestId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"send-email:" + actionRequestId,
				1);
		var configuration = new EmailRuntimeConfiguration(
				"smtp.example.test",
				587,
				"notice@example.test",
				"secret",
				"notice@example.test",
				true,
				true,
				false);
		when(configurationService.requireRuntimeConfiguration(context.organizationId())).thenReturn(configuration);
		when(configurationService.mailSender(configuration)).thenReturn(mailSender);
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
	void failsWithoutOnlineOrEnvironmentConfiguration() {
		var configurationService = mock(EmailConfigurationService.class);
		var tool = new SendEmailTool(mock(JavaMailSender.class), configurationService, mock(FileStorageService.class));
		var context = new ToolExecutionContext(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"send-email:test",
				1);
		when(configurationService.requireRuntimeConfiguration(context.organizationId()))
				.thenThrow(new EmailConfigurationException("尚未配置发件邮箱，请在设置页保存 SMTP 配置后再发送"));

		var result = tool.execute(context, Map.of("to", "hecheng@example.test"));

		assertThat(result.success()).isFalse();
		assertThat(result.message()).contains("设置页保存 SMTP 配置");
	}

	@Test
	void failsWithoutSendingWhenRequiredAttachmentIsMissing() {
		var mailSender = mock(JavaMailSender.class);
		var configurationService = mock(EmailConfigurationService.class);
		var fileStorageService = mock(FileStorageService.class);
		var tool = new SendEmailTool(mailSender, configurationService, fileStorageService);
		var context = new ToolExecutionContext(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"send-email:test",
				1);
		var configuration = new EmailRuntimeConfiguration(
				"smtp.example.test",
				587,
				"notice@example.test",
				"secret",
				"notice@example.test",
				true,
				true,
				false);
		when(configurationService.requireRuntimeConfiguration(context.organizationId())).thenReturn(configuration);
		when(fileStorageService.findRecentForCustomer(context.organizationId(), context.customerId(), 3)).thenReturn(List.of());

		var result = tool.execute(context, Map.of(
				"to", "hecheng@example.test",
				"subject", "报价文件",
				"body", "请查收附件。",
				"attachmentRequired", true));

		assertThat(result.success()).isFalse();
		assertThat(result.message()).contains("没有找到可发送的上传文件");
		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}
}
