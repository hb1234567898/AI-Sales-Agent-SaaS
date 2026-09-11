package com.yourcompany.salesagent.tool.registry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.yourcompany.salesagent.tool.domain.ToolNotFoundException;
import com.yourcompany.salesagent.tool.spi.AgentTool;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 启动时收集所有 {@link AgentTool} Bean 并注册。禁止重复 (name, version)，
 * 对应设计文档 8.4 节：数据库只存策略，不动态加载可执行代码。
 */
@Component
public class ToolRegistry {

	private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();
	private final List<AgentTool> candidates;

	public ToolRegistry(List<AgentTool> candidates) {
		this.candidates = candidates;
	}

	@PostConstruct
	public void register() {
		for (var tool : candidates) {
			var descriptor = tool.descriptor();
			var key = descriptor.name() + ":" + descriptor.version();
			if (tools.putIfAbsent(key, tool) != null) {
				throw new IllegalStateException("重复注册工具: " + key);
			}
		}
	}

	public AgentTool resolve(String name, String version) {
		var tool = tools.get(name + ":" + version);
		if (tool == null) {
			throw new ToolNotFoundException(name, version);
		}
		return tool;
	}
}
