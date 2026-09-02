package com.yourcompany.salesagent.tool.domain;

/**
 * 工具执行编排层的通用错误。
 */
public class ToolExecutionException extends RuntimeException {

	public ToolExecutionException(String message) {
		super(message);
	}
}
