package com.yourcompany.salesagent.tool.domain;

/**
 * 工具未在 {@link com.yourcompany.salesagent.tool.registry.ToolRegistry} 中注册。
 */
public class ToolNotFoundException extends RuntimeException {

	public ToolNotFoundException(String name, String version) {
		super("工具未注册: " + name + ":" + version);
	}
}
