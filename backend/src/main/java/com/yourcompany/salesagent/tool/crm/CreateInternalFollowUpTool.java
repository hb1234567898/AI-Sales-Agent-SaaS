package com.yourcompany.salesagent.tool.crm;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.yourcompany.salesagent.approval.infrastructure.ApprovalMapper;
import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;
import com.yourcompany.salesagent.tool.domain.ToolResult;
import com.yourcompany.salesagent.tool.domain.ToolRisk;
import com.yourcompany.salesagent.tool.spi.AgentTool;
import com.yourcompany.salesagent.tool.spi.ToolDescriptor;

import org.springframework.stereotype.Component;

/**
 * 在内部系统创建跟进任务（LOW 风险，自动执行）。沿用审批模块已有的
 * insertFollowUpFromApprovedAction，保证已批准的跟进建议落到 follow_up 表。
 */
@Component
public class CreateInternalFollowUpTool implements AgentTool {

	private final ApprovalMapper approvalMapper;

	public CreateInternalFollowUpTool(ApprovalMapper approvalMapper) {
		this.approvalMapper = approvalMapper;
	}

	@Override
	public ToolDescriptor descriptor() {
		return new ToolDescriptor("internal.follow_up.create", "v1", ToolRisk.LOW, false, Duration.ofSeconds(5), 0);
	}

	@Override
	public ToolResult execute(ToolExecutionContext context, Map<String, Object> payload) {
		approvalMapper.insertFollowUpFromApprovedAction(
				context.organizationId(), context.actionRequestId(), Instant.now());
		return ToolResult.success("已创建内部跟进任务", Map.of());
	}
}
