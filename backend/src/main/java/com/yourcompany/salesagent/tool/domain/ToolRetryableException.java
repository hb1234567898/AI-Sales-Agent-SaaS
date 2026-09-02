package com.yourcompany.salesagent.tool.domain;

/**
 * 工具抛此异常表示可重试错误（网络超时、429、5xx 等）。非可重试错误直接使用普通
 * RuntimeException，执行服务不会重试。
 */
public class ToolRetryableException extends RuntimeException {

	public ToolRetryableException(String message) {
		super(message);
	}

	public ToolRetryableException(String message, Throwable cause) {
		super(message, cause);
	}
}
