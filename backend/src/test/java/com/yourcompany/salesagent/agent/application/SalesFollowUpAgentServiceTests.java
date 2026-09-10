package com.yourcompany.salesagent.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.agent.infrastructure.AgentCandidateRow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.yourcompany.salesagent.agent.api.AgentRunCreateRequest;
import com.yourcompany.salesagent.agent.infrastructure.AgentRunRow;
import com.yourcompany.salesagent.agent.infrastructure.AgentWorkflowMapper;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.interaction.api.ChatAnalysisResponse;
import com.yourcompany.salesagent.interaction.application.ChatAnalysisService;
import com.yourcompany.salesagent.interaction.domain.ChatAnalysisStatus;

class SalesFollowUpAgentServiceTests {

	private static final UUID ORGANIZATION_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-09-09T01:30:00Z");

	private final AgentWorkflowMapper mapper = mock(AgentWorkflowMapper.class);
	private final ChatAnalysisService chatAnalysisService = mock(ChatAnalysisService.class);
	private final SalesFollowUpAgentService service = new SalesFollowUpAgentService(
			mapper,
			chatAnalysisService,
			Clock.fixed(NOW, ZoneOffset.UTC),
			ORGANIZATION_ID);

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	void recordsMcpAssistantTriggeredRunsInAgentHistory() {
		var principal = new AuthPrincipal(
				UUID.randomUUID(),
				ORGANIZATION_ID,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"sales@example.test",
				"销售",
				"团队",
				"SALES",
				NOW.plusSeconds(900));
		var configId = UUID.randomUUID();
		var conversationId = UUID.randomUUID();
		when(mapper.selectDefaultConfigId(ORGANIZATION_ID)).thenReturn(configId);
		when(mapper.selectCandidates(eq(ORGANIZATION_ID), any(), any(), anyInt())).thenReturn(List.of());
		when(mapper.selectRun(eq(ORGANIZATION_ID), any())).thenAnswer(invocation -> {
			var row = new AgentRunRow();
			row.setId(invocation.getArgument(1));
			row.setName("客户跟进建议 Agent");
			row.setTriggerType("MCP_ASSISTANT");
			row.setStatus("COMPLETED");
			row.setScope(Map.of("triggerType", "MCP_ASSISTANT", "conversationId", conversationId.toString()));
			row.setOutputSummary(Map.of("message", "没有需要审批的建议"));
			row.setQueuedAt(NOW);
			row.setStartedAt(NOW);
			row.setCompletedAt(NOW);
			row.setCreatedAt(NOW);
			return row;
		});
		var scope = ArgumentCaptor.forClass(Map.class);
		var inputSnapshot = ArgumentCaptor.forClass(Map.class);

		var run = service.runFromMcpAssistant(principal, new AgentRunCreateRequest(5, 30, null), conversationId);

		assertThat(run.triggerType()).isEqualTo("MCP_ASSISTANT");
		verify(mapper).insertRun(
				any(),
				eq(ORGANIZATION_ID),
				eq(configId),
				eq(principal.memberId()),
				eq("MCP_ASSISTANT"),
				eq("RUNNING"),
				any(),
				eq(null),
				scope.capture(),
				inputSnapshot.capture(),
				eq(NOW),
				eq(NOW));
		assertThat(scope.getValue())
				.containsEntry("triggerType", "MCP_ASSISTANT")
				.containsEntry("conversationId", conversationId.toString())
				.containsEntry("recentDays", 30)
				.containsEntry("maxCustomers", 5);
		assertThat(inputSnapshot.getValue())
				.containsEntry("requestedBy", principal.email())
				.containsEntry("triggerType", "MCP_ASSISTANT")
				.containsEntry("conversationId", conversationId.toString());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	void emailActionIncludesRecipientInPayloadAndApprovalPreview() {
		var principal = principal();
		var configId = UUID.randomUUID();
		var customerId = UUID.randomUUID();
		var interactionId = UUID.randomUUID();
		var ownerMemberId = UUID.randomUUID();
		var candidate = new AgentCandidateRow();
		candidate.setCustomerId(customerId);
		candidate.setCustomerName("宁波海天机械");
		candidate.setOwnerMemberId(ownerMemberId);
		candidate.setInteractionId(interactionId);
		when(mapper.selectDefaultConfigId(ORGANIZATION_ID)).thenReturn(configId);
		when(mapper.selectCandidates(eq(ORGANIZATION_ID), any(), any(), anyInt())).thenReturn(List.of(candidate));
		when(mapper.selectNotificationEmail(ORGANIZATION_ID, customerId, ownerMemberId)).thenReturn("hecheng@example.test");
		when(chatAnalysisService.analyze(customerId, interactionId)).thenReturn(new ChatAnalysisResponse(
				UUID.randomUUID(),
				interactionId,
				1,
				ChatAnalysisStatus.APPLIED,
				"客户希望本周看报价，需要安排跟进。",
				82,
				"HIGH",
				"POSITIVE",
				List.of("报价"),
				List.of(),
				List.of(),
				List.of(),
				List.of("SEND_EMAIL"),
				"发送报价跟进邮件",
				null,
				null,
				null,
				List.of("客户说本周想看报价"),
				"QWEN",
				"qwen-plus",
				"sales-follow-up-v1",
				NOW,
				NOW));
		when(mapper.selectRun(eq(ORGANIZATION_ID), any())).thenAnswer(invocation -> {
			var row = new AgentRunRow();
			row.setId(invocation.getArgument(1));
			row.setName("客户跟进建议 Agent");
			row.setTriggerType("MANUAL");
			row.setStatus("WAITING_APPROVAL");
			row.setScope(Map.of());
			row.setOutputSummary(Map.of("message", "已生成待审批跟进建议"));
			row.setPendingApprovalCount(1);
			row.setQueuedAt(NOW);
			row.setStartedAt(NOW);
			row.setCreatedAt(NOW);
			return row;
		});
		var payload = ArgumentCaptor.forClass(Map.class);
		var preview = ArgumentCaptor.forClass(Map.class);

		service.runNow(principal, new AgentRunCreateRequest(5, 30, List.of(customerId)));

		verify(mapper).insertActionRequest(
				any(),
				eq(ORGANIZATION_ID),
				any(),
				any(),
				eq(customerId),
				eq(principal.memberId()),
				eq("SEND_EMAIL"),
				eq("MEDIUM"),
				eq("AWAITING_APPROVAL"),
				eq("email.send"),
				eq("v1"),
				eq(true),
				eq("REQUIRE_APPROVAL"),
				any(),
				payload.capture(),
				any(),
				preview.capture(),
				any(),
				any());
		assertThat(payload.getValue())
				.containsEntry("to", "hecheng@example.test")
				.containsEntry("subject", "跟进提醒：宁波海天机械");
		assertThat(payload.getValue().get("body")).asString().contains("客户希望本周看报价");
		assertThat(preview.getValue())
				.containsEntry("to", "hecheng@example.test")
				.containsEntry("subject", "跟进提醒：宁波海天机械")
				.containsEntry("action", "发送报价跟进邮件");
		assertThat(preview.getValue().get("body")).asString().contains("客户希望本周看报价");
	}

	private static AuthPrincipal principal() {
		return new AuthPrincipal(
				UUID.randomUUID(),
				ORGANIZATION_ID,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"sales@example.test",
				"销售",
				"团队",
				"SALES",
				NOW.plusSeconds(900));
	}
}
