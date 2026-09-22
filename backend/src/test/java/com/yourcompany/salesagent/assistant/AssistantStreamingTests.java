package com.yourcompany.salesagent.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.ai.application.*;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelClient;
import com.yourcompany.salesagent.agent.application.SalesFollowUpAgentService;
import com.yourcompany.salesagent.approval.application.ApprovalService;
import com.yourcompany.salesagent.assistant.api.AssistantChatResponse;
import com.yourcompany.salesagent.assistant.api.AssistantChatRequest;
import com.yourcompany.salesagent.assistant.application.*;
import com.yourcompany.salesagent.assistant.infrastructure.*;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.customer.api.CustomerResponse;
import com.yourcompany.salesagent.customer.application.CustomerService;
import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.customer.domain.CustomerStage;
import com.yourcompany.salesagent.customer.domain.CustomerStatus;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.file.domain.UploadedFile;
import com.yourcompany.salesagent.followup.application.FollowUpService;
import com.yourcompany.salesagent.interaction.application.InteractionService;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

class AssistantStreamingTests {
	final AssistantConversationMapper mapper = mock(AssistantConversationMapper.class);
	final ApprovalService approvals = mock(ApprovalService.class);
	final AiModelService settings = mock(AiModelService.class);
	final QwenModelClient model = mock(QwenModelClient.class);
	final ModelCallRecorder modelCallRecorder = mock(ModelCallRecorder.class);
	final FileStorageService fileStorageService = mock(FileStorageService.class);
	final CustomerService customers = mock(CustomerService.class);
	final SalesFollowUpAgentService agentService = mock(SalesFollowUpAgentService.class);
	final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
	final UUID conversationId = UUID.randomUUID();
	final AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
			UUID.randomUUID(), "sales@example.test", "销售", "团队", "SALES", Instant.now().plusSeconds(900));
	final List<String> events = new ArrayList<>();
	AssistantChatResponse saved;
	AssistantChatService service;

	@BeforeEach
	void setup() {
		when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		doAnswer(invocation -> { events.add("commit"); return null; }).when(tx).commit(any());
		when(approvals.findApprovals("PENDING", 0, 10)).thenReturn(Page.of(1, 10));
		when(settings.requireRuntimeConfiguration(any(), any())).thenReturn(new AiModelRuntimeConfiguration("QWEN", "test", "https://example.test", "test-key"));
		service = new AssistantChatService(mapper, customers, mock(InteractionService.class),
				agentService, approvals, mock(FollowUpService.class),
				JsonMapper.builder().build(), Clock.systemUTC(), settings, model, modelCallRecorder, fileStorageService, tx);
	}

	void receive(String type, Object data) {
		events.add(type);
		if (type.equals("done")) saved = (AssistantChatResponse) data;
	}

	@Test
	void emitsModelDeltasBeforeCompletionAndDoneAfterCommit() {
		when(model.streamAssistantReplyWithUsage(any(), anyString(), anyString())).thenReturn(Flux.just(
						new QwenModelClient.QwenStreamChunk("没有", null, null, "test"),
						new QwenModelClient.QwenStreamChunk("待审批建议", new ModelUsage(20, 8, 28, 0L, null), "chatcmpl-test", "test"))
				.doOnComplete(() -> assertThat(events).contains("delta").doesNotContain("done")));
		service.streamChat(principal, conversationId, "查看待审批", null, this::receive);
		assertThat(saved.content()).isEqualTo("没有待审批建议");
		assertThat(events).containsSubsequence("tool", "result", "delta", "delta", "commit", "done");
		assertThat(saved.toolTraces()).allMatch(trace -> trace.status().equals("SUCCEEDED"));
		assertThat(saved.data()).containsKey("modelUsage");
		verify(approvals, times(1)).findApprovals("PENDING", 0, 10);
		verify(modelCallRecorder).record(any());
	}

	@Test
	void keepsPartialAnswerAndBusinessResultOnModelFailureWithoutRepeatingTools() {
		when(model.streamAssistantReplyWithUsage(any(), anyString(), anyString())).thenReturn(
				Flux.concat(Flux.just(new QwenModelClient.QwenStreamChunk("已查询", null, null, "test")),
						Flux.error(new IllegalStateException("secret provider detail"))));
		service.streamChat(principal, conversationId, "查看待审批", null, this::receive);
		assertThat(saved.content()).startsWith("已查询").contains("现在没有待审批建议").doesNotContain("secret provider detail");
		assertThat(saved.data()).containsEntry("streamStatus", "INTERRUPTED");
		assertThat(events).containsSubsequence("commit", "error", "done");
		verify(approvals, times(1)).findApprovals("PENDING", 0, 10);
	}

	@Test
	void stillReturnsBusinessResultsWithoutConfiguredModel() {
		when(settings.requireRuntimeConfiguration(any(), any())).thenThrow(new AiModelNotConfiguredException("未配置"));
		service.streamChat(principal, conversationId, "查看待审批", null, this::receive);
		assertThat(saved.content()).isEqualTo("现在没有待审批建议。");
		verifyNoInteractions(model);
	}

	@Test
	void doesNotEmitDoneIfHistoryCannotBeSaved() {
		when(settings.requireRuntimeConfiguration(any(), any())).thenThrow(new AiModelNotConfiguredException("未配置"));
		when(mapper.insertMessage(any(), any(), any(), anyString(), anyString(), any(), anyString(), anyMap(), any()))
				.thenThrow(new IllegalStateException("database unavailable"));
		assertThatThrownBy(() -> service.streamChat(principal, conversationId, "查看待审批", null, this::receive)).isInstanceOf(IllegalStateException.class);
		assertThat(events).doesNotContain("done");
	}

	@Test
	void refusesAnotherMembersConversationBeforeExecutingTools() {
		when(mapper.selectConversation(principal.organizationId(), conversationId)).thenReturn(new AssistantConversationRow(
				conversationId, principal.organizationId(), UUID.randomUUID(), UUID.randomUUID(), "会话", "WEB", "OPEN", null, null, null, 0));
		assertThatThrownBy(() -> service.beginStream(principal, new AssistantChatRequest(conversationId, "查看待审批", null, "WEB")))
				.isInstanceOf(AssistantWorkflowException.class);
		verifyNoInteractions(approvals, model);
	}

	@Test
	void savesAttachmentPreviewWithUserMessageBeforeStreaming() {
		var fileId = UUID.randomUUID();
		var now = Instant.parse("2026-09-14T09:00:00Z");
		var file = UploadedFile.create(fileId, principal.organizationId(), principal.memberId(), null,
				"quote.pdf", "application/pdf", "pdf".getBytes(), "hash", now);
		when(mapper.selectConversation(principal.organizationId(), conversationId)).thenReturn(new AssistantConversationRow(
				conversationId, principal.organizationId(), principal.userId(), principal.memberId(), "会话", "WEB", "OPEN", null, now, now, 0));
		when(fileStorageService.requireActive(principal.organizationId(), fileId)).thenReturn(file);
		when(fileStorageService.preview(List.of(file))).thenReturn(List.of(Map.of(
				"id", fileId.toString(),
				"name", "quote.pdf",
				"contentType", "application/pdf",
				"sizeBytes", 3L)));

		service.beginStream(principal, new AssistantChatRequest(conversationId, "运行 Agent 分析云岚科技", List.of(fileId), "WEB"));

		verify(mapper).insertMessage(any(), eq(principal.organizationId()), eq(conversationId), eq("USER"),
				eq("运行 Agent 分析云岚科技"), isNull(), eq("[]"), argThat(data -> data.containsKey("attachments")), any());
	}

	@Test
	void asksForAttachmentInsteadOfReturningGenericHelpForDocumentEmail() {
		when(settings.requireRuntimeConfiguration(any(), any())).thenThrow(new AiModelNotConfiguredException("未配置"));

		service.streamChat(principal, conversationId, "给和成科技发送方案", null, this::receive);

		assertThat(saved.content()).contains("需要带上文件").contains("点击发送后上传");
		assertThat(saved.data()).containsEntry("attachmentRequired", true);
		assertThat(saved.toolTraces()).anyMatch(trace -> trace.name().equals("email.send.prepare"));
		verifyNoInteractions(agentService);
	}

	@Test
	void createsApprovalForDocumentEmailWhenAttachmentIsProvided() {
		when(settings.requireRuntimeConfiguration(any(), any())).thenThrow(new AiModelNotConfiguredException("未配置"));
		var now = Instant.parse("2026-09-14T09:00:00Z");
		var fileId = UUID.randomUUID();
		var customerId = UUID.randomUUID();
		var approvalId = UUID.randomUUID();
		var runId = UUID.randomUUID();
		var actionRequestId = UUID.randomUUID();
		var file = UploadedFile.create(fileId, principal.organizationId(), principal.memberId(), null,
				"和成科技方案.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				"docx".getBytes(), "hash", now);
		var attachmentPreview = List.<Map<String, Object>>of(Map.of("id", fileId.toString(), "name", "和成科技方案.docx"));
		var customer = new CustomerResponse(customerId, "和成科技", null, "软件服务", "9999+",
				CustomerStage.LEAD, CustomerStatus.ACTIVE, CustomerSource.CHAT, principal.memberId(), "蔡景辉",
				85, null, null, now, null,
				new CustomerResponse.PrimaryContactResponse("李春和", "2564942830@qq.com", "13800000011"), now, now, 1);
		var page = new Page<CustomerResponse>(1, 5).setRecords(List.of(customer));
		when(customers.findCustomers("和成科技", null, null, 0, 5)).thenReturn(page);
		when(fileStorageService.requireActive(principal.organizationId(), fileId)).thenReturn(file);
		when(fileStorageService.preview(List.of(file))).thenReturn(attachmentPreview);
		when(agentService.proposeDocumentEmailFromMcpAssistant(principal, customer, conversationId, "方案", List.of(fileId)))
				.thenReturn(new SalesFollowUpAgentService.McpDocumentEmailResult(
						runId, approvalId, actionRequestId, "2564942830@qq.com", "和成科技方案"));

		service.streamChat(principal, conversationId, "给和成科技发送方案", List.of(fileId), this::receive);

		assertThat(saved.content()).contains("当前尚未发送").contains("审批通过后");
		assertThat(saved.data()).containsEntry("approvalId", approvalId).containsEntry("to", "2564942830@qq.com");
		verify(agentService).proposeDocumentEmailFromMcpAssistant(principal, customer, conversationId, "方案", List.of(fileId));
	}
}
