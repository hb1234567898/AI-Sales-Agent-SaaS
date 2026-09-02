package com.yourcompany.salesagent.tool.spi;

import com.yourcompany.salesagent.tool.domain.ToolRisk;

import java.time.Duration;

/**
 * 工具的静态描述，在注册时固定。对应设计文档 8.4 节的 ToolDescriptor。
 */
public record ToolDescriptor(
		String name,
		String version,
		ToolRisk risk,
		boolean readOnly,
		Duration timeout,
		int maxRetries) {
}
