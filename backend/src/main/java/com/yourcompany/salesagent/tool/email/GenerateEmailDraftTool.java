package com.yourcompany.salesagent.tool.email;

import java.time.Duration;
import java.util.Map;

import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;
import com.yourcompany.salesagent.tool.domain.ToolResult;
import com.yourcompany.salesagent.tool.domain.ToolRisk;
import com.yourcompany.salesagent.tool.spi.AgentTool;
import com.yourcompany.salesagent.tool.spi.ToolDescriptor;

import org.springframework.stereotype.Component;

/**
 * 生成邮件草稿（LOW 风险，可自动执行，不产生外部副作用）。草稿内容由 Agent 在
 * action_request.payload 中给出，此处仅确认就绪，真正"发送"仍走 SendEmailTool。
 */
@Component
public class GenerateEmailDraftTool implements AgentTool {

	@Override
	public ToolDescriptor descriptor() {
		return new ToolDescriptor("email.draft.generate", "v1", ToolRisk.LOW, true, Duration.ofSeconds(10), 0);
	}

	@Override
	public ToolResult execute(ToolExecutionContext context, Map<String, Object> payload) {
		var subject = asString(payload.get("subject"));
		return ToolResult.success("邮件草稿已生成", Map.of("subject", subject));
	}

	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}
}
