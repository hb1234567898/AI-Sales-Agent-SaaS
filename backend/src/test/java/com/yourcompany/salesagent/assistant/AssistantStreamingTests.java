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
import com.yourcompany.salesagent.customer.application.CustomerService;
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
		when(settings.requireRuntimeConfiguration(any())).thenReturn(new AiModelRuntimeConfiguration("QWEN", "test", "https://example.test", "test-key"));
		service = new AssistantChatService(mapper, mock(CustomerService.class), mock(InteractionService.class),
				mock(SalesFollowUpAgentService.class), approvals, mock(FollowUpService.class),
				JsonMapper.builder().build(), Clock.systemUTC(), settings, model, modelCallRecorder, tx);
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
		service.streamChat(principal, conversationId, "查看待审批", this::receive);
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
		service.streamChat(principal, conversationId, "查看待审批", this::receive);
		assertThat(saved.content()).startsWith("已查询").contains("现在没有待审批建议").doesNotContain("secret provider detail");
		assertThat(saved.data()).containsEntry("streamStatus", "INTERRUPTED");
		assertThat(events).containsSubsequence("commit", "error", "done");
		verify(approvals, times(1)).findApprovals("PENDING", 0, 10);
	}

	@Test
	void stillReturnsBusinessResultsWithoutConfiguredModel() {
		when(settings.requireRuntimeConfiguration(any())).thenThrow(new AiModelNotConfiguredException("未配置"));
		service.streamChat(principal, conversationId, "查看待审批", this::receive);
		assertThat(saved.content()).isEqualTo("现在没有待审批建议。");
		verifyNoInteractions(model);
	}

	@Test
	void doesNotEmitDoneIfHistoryCannotBeSaved() {
		when(settings.requireRuntimeConfiguration(any())).thenThrow(new AiModelNotConfiguredException("未配置"));
		when(mapper.insertMessage(any(), any(), any(), anyString(), anyString(), any(), anyString(), anyMap(), any()))
				.thenThrow(new IllegalStateException("database unavailable"));
		assertThatThrownBy(() -> service.streamChat(principal, conversationId, "查看待审批", this::receive)).isInstanceOf(IllegalStateException.class);
		assertThat(events).doesNotContain("done");
	}

	@Test
	void refusesAnotherMembersConversationBeforeExecutingTools() {
		when(mapper.selectConversation(principal.organizationId(), conversationId)).thenReturn(new AssistantConversationRow(
				conversationId, principal.organizationId(), UUID.randomUUID(), UUID.randomUUID(), "会话", "WEB", "OPEN", null, null, null, 0));
		assertThatThrownBy(() -> service.beginStream(principal, new AssistantChatRequest(conversationId, "查看待审批", "WEB")))
				.isInstanceOf(AssistantWorkflowException.class);
		verifyNoInteractions(approvals, model);
	}
}
