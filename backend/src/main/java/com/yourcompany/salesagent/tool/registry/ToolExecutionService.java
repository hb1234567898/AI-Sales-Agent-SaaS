package com.yourcompany.salesagent.tool.registry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.yourcompany.salesagent.approval.infrastructure.ApprovalMapper;
import com.yourcompany.salesagent.tool.domain.ToolExecutionException;
import com.yourcompany.salesagent.tool.domain.ToolNotFoundException;
import com.yourcompany.salesagent.tool.domain.ToolResult;
import com.yourcompany.salesagent.tool.domain.ToolRetryableException;
import com.yourcompany.salesagent.tool.infrastructure.ActionRequestRow;
import com.yourcompany.salesagent.tool.infrastructure.ToolExecutionMapper;
import com.yourcompany.salesagent.tool.spi.AgentTool;
import com.yourcompany.salesagent.tool.spi.ToolDescriptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工具执行的编排核心。在事务外运行（{@link Propagation#NOT_SUPPORTED}），避免持有
 * 数据库事务等待外部 SMTP / CRM 响应。执行顺序对应设计文档 8.5 节：
 * 锁行 -> 解析工具 -> 执行 -> 写 ToolInvocation -> 成功回写 Interaction -> 置 SUCCEEDED/FAILED。
 */
@Service
public class ToolExecutionService {

	private final ToolRegistry registry;
	private final ToolExecutionMapper mapper;
	private final ApprovalMapper approvalMapper;
	private final Clock clock;
	private final UUID organizationId;

	public ToolExecutionService(
			ToolRegistry registry,
			ToolExecutionMapper mapper,
			ApprovalMapper approvalMapper,
			Clock clock,
			@Value("${app.demo.organization-id}") UUID organizationId) {
		this.registry = registry;
		this.mapper = mapper;
		this.approvalMapper = approvalMapper;
		this.clock = clock;
		this.organizationId = organizationId;
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void execute(UUID actionRequestId) {
		var row = mapper.selectActionRequestForExecution(organizationId, actionRequestId);
		if (row == null) {
			throw new ToolExecutionException("动作不存在: " + actionRequestId);
		}
		// 只执行处于已批准/执行中状态的动作；被拒绝或过期的一律跳过。
		if (!"APPROVED".equals(row.getStatus()) && !"EXECUTING".equals(row.getStatus())) {
			return;
		}
		// 乐观抢占：只有一个执行者能把动作置为 EXECUTING，其余立刻放弃。
		var locked = mapper.lockActionRequestForExecution(organizationId, actionRequestId, clock.instant());
		if (locked == 0) {
			return;
		}

		AgentTool tool;
		try {
			tool = registry.resolve(row.getToolName(), row.getToolVersion());
		}
		catch (ToolNotFoundException e) {
			mapper.updateActionFailed(organizationId, actionRequestId, "TOOL_NOT_FOUND", e.getMessage(), clock.instant());
			return;
		}

		var descriptor = tool.descriptor();
		var context = new com.yourcompany.salesagent.tool.domain.ToolExecutionContext(
				organizationId, row.getId(), row.getRunId(), row.getStepId(),
				row.getCustomerId(), row.getIdempotencyKey(), 1);

		var maxRetries = Math.max(0, descriptor.maxRetries());
		var result = ToolResult.failure("未执行");
		var failureCode = "EXECUTION_FAILED";
		var failureMessage = "未知错误";

		for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
			var start = clock.instant();
			try {
				mapper.insertToolInvocation(organizationId, actionRequestId, row.getRunId(), row.getStepId(),
						descriptor.name(), descriptor.version(), attempt, row.getIdempotencyKey(), "STARTED",
						row.getPayload(), start);
				result = tool.execute(context, row.getPayload());
				var latency = Duration.between(start, clock.instant()).toMillis();
				if (result.success()) {
					mapper.completeToolInvocation(organizationId, actionRequestId, attempt, "SUCCEEDED",
							toMap(result), result.externalOperationId(), false, null, null, latency, clock.instant());
					break;
				}
				failureCode = "TOOL_REPORTED_FAILURE";
				failureMessage = result.message();
				mapper.completeToolInvocation(organizationId, actionRequestId, attempt, "FAILED",
						toMap(result), result.externalOperationId(), false, failureCode, failureMessage, latency, clock.instant());
				// 工具明确报告失败，不盲目重试外部动作（避免重复触达客户）。
				break;
			}
			catch (ToolRetryableException e) {
				var latency = Duration.between(start, clock.instant()).toMillis();
				failureCode = "RETRYABLE_ERROR";
				failureMessage = e.getMessage();
				mapper.completeToolInvocation(organizationId, actionRequestId, attempt, "FAILED",
						Map.of(), null, true, failureCode, failureMessage, latency, clock.instant());
				// 继续下一次重试
			}
			catch (RuntimeException e) {
				var latency = Duration.between(start, clock.instant()).toMillis();
				failureCode = "UNEXPECTED_ERROR";
				failureMessage = e.getMessage();
				mapper.completeToolInvocation(organizationId, actionRequestId, attempt, "FAILED",
						Map.of(), null, false, failureCode, failureMessage, latency, clock.instant());
				break;
			}
		}

		if (result.success()) {
			persistSuccessSideEffects(row);
			mapper.updateActionSucceeded(organizationId, actionRequestId, clock.instant());
		}
		else {
			mapper.updateActionFailed(organizationId, actionRequestId, failureCode, failureMessage, clock.instant());
		}
	}

	/**
	 * 执行成功后把结果落回业务系统：创建内部跟进动作写 follow_up，其余动作回写 interaction 时间线。
	 */
	private void persistSuccessSideEffects(ActionRequestRow row) {
		if ("CREATE_INTERNAL_FOLLOW_UP".equals(row.getActionType())) {
			approvalMapper.insertFollowUpFromApprovedAction(organizationId, row.getId(), clock.instant());
			return;
		}
		var spec = interactionSpecFor(row.getActionType());
		if (spec == null) {
			return;
		}
		var payload = row.getPayload();
		var subject = asString(payload.get("subject"));
		var body = asString(payload.get("body"));
		mapper.insertInteractionFromExecution(organizationId, row.getCustomerId(), row.getRequestedByMemberId(),
				spec.type(), spec.direction(), clock.instant(), subject, body, payload, clock.instant());
	}

	private static InteractionSpec interactionSpecFor(String actionType) {
		return switch (actionType) {
			case "SEND_EMAIL" -> new InteractionSpec("EMAIL_SENT", "OUTBOUND");
			case "CREATE_CRM_TASK" -> new InteractionSpec("TASK_CREATED", "NONE");
			case "GENERATE_EMAIL_DRAFT" -> new InteractionSpec("NOTE", "NONE");
			default -> null;
		};
	}

	private record InteractionSpec(String type, String direction) {
	}

	private static Map<String, Object> toMap(ToolResult result) {
		var map = new LinkedHashMap<String, Object>();
		if (result.externalOperationId() != null) {
			map.put("externalOperationId", result.externalOperationId());
		}
		map.put("message", result.message());
		map.putAll(result.output());
		return map;
	}

	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
