package com.yourcompany.salesagent.tool.spi;

import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;
import com.yourcompany.salesagent.tool.domain.ToolResult;

import java.util.Map;

/**
 * 一个可执行工具。框架通过 {@link #descriptor()} 注册元数据，并在隔离的事务外调用
 * {@link #execute(ToolExecutionContext, Map)}。工具本身不持有数据库事务，外部副作用
 * （如发送邮件）在此完成，结果由 ToolExecutionService 落库。
 *
 * 实现类通过 Spring 注册为 Bean 即被 {@code ToolRegistry} 自动收集，禁止重复 (name, version)。
 */
public interface AgentTool {

	ToolDescriptor descriptor();

	ToolResult execute(ToolExecutionContext context, Map<String, Object> payload);
}
