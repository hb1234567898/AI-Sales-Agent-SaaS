package com.yourcompany.salesagent.approval.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.approval.api.ApprovalDecisionRequest;
import com.yourcompany.salesagent.approval.api.ApprovalResponse;
import com.yourcompany.salesagent.approval.infrastructure.ApprovalMapper;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.tool.infrastructure.ToolExecutionMapper;
import com.yourcompany.salesagent.tool.registry.ToolExecutionService;

@Service
public class ApprovalService {

	private final ApprovalMapper mapper;
	private final ToolExecutionMapper toolMapper;
	private final ToolExecutionService toolExecutionService;
	private final Clock clock;
	private final UUID organizationId;

	public ApprovalService(
			ApprovalMapper mapper,
			ToolExecutionMapper toolMapper,
			ToolExecutionService toolExecutionService,
			Clock clock,
			@Value("${app.demo.organization-id}") UUID organizationId) {
		this.mapper = mapper;
		this.toolMapper = toolMapper;
		this.toolExecutionService = toolExecutionService;
		this.clock = clock;
		this.organizationId = organizationId;
	}

	@Transactional(readOnly = true)
	public IPage<ApprovalResponse> findApprovals(String status, int page, int size) {
		var rows = mapper.selectApprovals(Page.of(page + 1L, size), organizationId, normalizeStatus(status));
		return new Page<ApprovalResponse>(rows.getCurrent(), rows.getSize(), rows.getTotal())
				.setRecords(rows.getRecords().stream().map(ApprovalResponse::from).toList());
	}

	@Transactional
	public ApprovalResponse approve(AuthPrincipal principal, UUID approvalId, ApprovalDecisionRequest request) {
		decide(principal, approvalId, request, "APPROVED");
		var approval = requireApproval(approvalId);
		// 动作进入 APPROVED，交由 Tool 执行层真正发生副作用（发邮件 / 建任务 / 回写）。
		toolMapper.markActionApproved(organizationId, approval.actionRequestId(), clock.instant());
		toolExecutionService.execute(approval.actionRequestId());
		var now = clock.instant();
		mapper.refreshRunApprovalState(organizationId, approval.runId(), now);
		return requireApproval(approvalId);
	}

	@Transactional
	public ApprovalResponse reject(AuthPrincipal principal, UUID approvalId, ApprovalDecisionRequest request) {
		decide(principal, approvalId, request, "REJECTED");
		var approval = requireApproval(approvalId);
		var now = clock.instant();
		mapper.markActionRejected(organizationId, approval.actionRequestId(), now);
		mapper.refreshRunApprovalState(organizationId, approval.runId(), now);
		return requireApproval(approvalId);
	}

	private void decide(AuthPrincipal principal, UUID approvalId, ApprovalDecisionRequest request, String decision) {
		var updated = mapper.updateDecision(
				organizationId,
				approvalId,
				principal.memberId(),
				decision,
				request.comment(),
				request.expectedVersion(),
				clock.instant());
		if (updated == 0) {
			throw new ApprovalWorkflowException("审批已被处理或版本已变化，请刷新后重试");
		}
	}

	private ApprovalResponse requireApproval(UUID approvalId) {
		var row = mapper.selectApproval(organizationId, approvalId);
		if (row == null) {
			throw new ApprovalWorkflowException("审批记录不存在");
		}
		return ApprovalResponse.from(row);
	}

	private static String normalizeStatus(String status) {
		return status == null || status.isBlank() ? "PENDING" : status.strip().toUpperCase();
	}
}
