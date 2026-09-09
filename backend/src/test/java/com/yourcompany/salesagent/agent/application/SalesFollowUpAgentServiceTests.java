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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.yourcompany.salesagent.agent.api.AgentRunCreateRequest;
import com.yourcompany.salesagent.agent.infrastructure.AgentRunRow;
import com.yourcompany.salesagent.agent.infrastructure.AgentWorkflowMapper;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.interaction.application.ChatAnalysisService;

class SalesFollowUpAgentServiceTests {

	private static final UUID ORGANIZATION_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-09-09T01:30:00Z");

	private final AgentWorkflowMapper mapper = mock(AgentWorkflowMapper.class);
	private final SalesFollowUpAgentService service = new SalesFollowUpAgentService(
			mapper,
			mock(ChatAnalysisService.class),
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
}
