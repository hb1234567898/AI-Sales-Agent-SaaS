package com.yourcompany.salesagent.tool.crm;

import java.time.Duration;
import java.util.Map;

import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;
import com.yourcompany.salesagent.tool.domain.ToolResult;
import com.yourcompany.salesagent.tool.domain.ToolRisk;
import com.yourcompany.salesagent.tool.spi.AgentTool;
import com.yourcompany.salesagent.tool.spi.ToolDescriptor;

import org.springframework.stereotype.Component;

/**
 * 在 CRM 创建任务（MEDIUM 风险，默认需审批）。MVP 中 CRM 任务以 interaction 时间线
 * （TASK_CREATED）的形式记录，由 ToolExecutionService 在执行成功后回写。
 */
@Component
public class CreateCrmTaskTool implements AgentTool {

	@Override
	public ToolDescriptor descriptor() {
		return new ToolDescriptor("crm.task.create", "v1", ToolRisk.MEDIUM, false, Duration.ofSeconds(10), 0);
	}

	@Override
	public ToolResult execute(ToolExecutionContext context, Map<String, Object> payload) {
		var title = asString(payload.get("subject"));
		return ToolResult.success("已创建 CRM 任务", Map.of("title", title));
	}

	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
