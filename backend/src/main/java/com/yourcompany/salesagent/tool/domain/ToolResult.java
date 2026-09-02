package com.yourcompany.salesagent.tool.domain;

import java.util.Collections;
import java.util.Map;

/**
 * 工具一次执行的结果。success=false 时由 ToolExecutionService 写入 action_request 的
 * failure_code / failure_message，不会盲目重试不可恢复的错误。
 */
public record ToolResult(
		boolean success,
		String externalOperationId,
		String message,
		Map<String, Object> output) {

	public static ToolResult success(String message, Map<String, Object> output) {
		return new ToolResult(true, null, message, output == null ? Collections.emptyMap() : output);
	}

	public static ToolResult success(String externalOperationId, String message, Map<String, Object> output) {
		return new ToolResult(true, externalOperationId, message, output == null ? Collections.emptyMap() : output);
	}

	public static ToolResult failure(String message) {
		return new ToolResult(false, null, message, Collections.emptyMap());
	}
}
