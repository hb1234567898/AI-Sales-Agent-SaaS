package com.yourcompany.salesagent.tool.domain;

import java.util.UUID;

/**
 * 工具执行时的不可变上下文。由 ToolExecutionService 在锁行 action_request 后构建，
 * 携带租户、动作、客户、幂等键与重试次数，供具体工具读取而不直接接触持久层。
 */
public record ToolExecutionContext(
		UUID organizationId,
		UUID actionRequestId,
		UUID runId,
		UUID stepId,
		UUID customerId,
		String idempotencyKey,
		int attempt) {
}
