package com.yourcompany.salesagent.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.yourcompany.salesagent.approval.api.ApprovalDecisionRequest;
import com.yourcompany.salesagent.approval.infrastructure.ApprovalMapper;
import com.yourcompany.salesagent.approval.infrastructure.ApprovalRow;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.tool.infrastructure.ToolExecutionMapper;
import com.yourcompany.salesagent.tool.registry.ToolExecutionService;

class ApprovalServiceTests {

	private static final UUID ORGANIZATION_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");

	private final ApprovalMapper mapper = mock(ApprovalMapper.class);
	private final ToolExecutionMapper toolMapper = mock(ToolExecutionMapper.class);
	private final ToolExecutionService toolExecutionService = mock(ToolExecutionService.class);
	private final List<String> events = new ArrayList<>();
	private final TransactionTemplate transactions = new TransactionTemplate() {
		@Override
		public <T> T execute(TransactionCallback<T> action) {
			events.add("tx:start");
			try {
				return action.doInTransaction(mock(TransactionStatus.class));
			}
			finally {
				events.add("tx:end");
			}
		}
	};
	private final ApprovalService service = new ApprovalService(
			mapper,
			toolMapper,
			toolExecutionService,
			transactions,
			Clock.fixed(NOW, ZoneOffset.UTC),
			ORGANIZATION_ID);

	@Test
	void findsApprovalForCurrentOrganization() {
		var approvalId = UUID.randomUUID();
		var row = approval(approvalId, UUID.randomUUID(), UUID.randomUUID(), "PENDING");
		when(mapper.selectApproval(ORGANIZATION_ID, approvalId)).thenReturn(row);

		var response = service.findApproval(approvalId);

		assertThat(response.id()).isEqualTo(approvalId);
		assertThat(response.customerName()).isEqualTo("宁波海天机械");
		assertThat(response.version()).isEqualTo(2L);
	}

	@Test
	void approvesActionBeforeExecutingToolInSeparateTransaction() {
		var principal = principal();
		var approvalId = UUID.randomUUID();
		var actionRequestId = UUID.randomUUID();
		var runId = UUID.randomUUID();
		when(mapper.updateDecision(eq(ORGANIZATION_ID), eq(approvalId), eq(principal.memberId()),
				eq("APPROVED"), any(), any(), eq(NOW))).thenAnswer(invocation -> {
			events.add("approval:approved");
			return 1;
		});
		when(mapper.selectApproval(ORGANIZATION_ID, approvalId)).thenAnswer(invocation -> {
			events.add("approval:select");
			return approval(approvalId, actionRequestId, runId, events.contains("tool:execute") ? "SUCCEEDED" : "APPROVED");
		});
		doAnswer(invocation -> {
			events.add("action:approved");
			return 1;
		}).when(toolMapper).markActionApproved(ORGANIZATION_ID, actionRequestId, NOW);
		doAnswer(invocation -> {
			events.add("tool:execute");
			return null;
		}).when(toolExecutionService).execute(actionRequestId);
		when(mapper.refreshRunApprovalState(ORGANIZATION_ID, runId, NOW)).thenAnswer(invocation -> {
			events.add("run:refresh");
			return 1;
		});

		var response = service.approve(principal, approvalId, new ApprovalDecisionRequest(null, "同意发送"));

		assertThat(response.actionStatus()).isEqualTo("SUCCEEDED");
		assertThat(events).containsExactly(
				"tx:start",
				"approval:approved",
				"approval:select",
				"action:approved",
				"tx:end",
				"tool:execute",
				"tx:start",
				"run:refresh",
				"approval:select",
				"tx:end");
	}

	private static AuthPrincipal principal() {
		return new AuthPrincipal(
				UUID.randomUUID(),
				ORGANIZATION_ID,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"sales@example.test",
				"销售",
				"演示团队",
				"OWNER",
				NOW.plusSeconds(900));
	}

	private static ApprovalRow approval(UUID approvalId, UUID actionRequestId, UUID runId, String actionStatus) {
		var row = new ApprovalRow();
		row.setId(approvalId);
		row.setActionRequestId(actionRequestId);
		row.setRunId(runId);
		row.setCustomerId(UUID.randomUUID());
		row.setCustomerName("宁波海天机械");
		row.setActionType("SEND_EMAIL");
		row.setRiskLevel("HIGH");
		row.setActionStatus(actionStatus);
		row.setStatus("APPROVED");
		row.setReason("客户需要报价方案");
		row.setPreview(Map.of("to", "hecheng@example.test"));
		row.setRequester("sales@example.test");
		row.setVersion(2L);
		row.setRequestedAt(NOW.minusSeconds(60));
		row.setActionCompletedAt("SUCCEEDED".equals(actionStatus) ? NOW : null);
		return row;
	}
}
