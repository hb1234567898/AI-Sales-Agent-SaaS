package com.yourcompany.salesagent.ai.infrastructure;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class QwenModelClient {

	private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

	public QwenModelClient(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
		this.chatClientBuilderProvider = chatClientBuilderProvider;
	}

	public boolean isAvailable() {
		return chatClientBuilderProvider.getIfAvailable() != null;
	}

	public String testConnection() {
		var builder = chatClientBuilderProvider.getIfAvailable();
		if (builder == null) {
			throw new IllegalStateException("Spring AI 千问客户端尚未启用");
		}
		return builder.build()
				.prompt()
				.system("你是销售 Agent 的模型连接检查器。不要调用工具，不要补充解释。")
				.user("请只回复四个汉字：连接成功")
				.call()
				.content();
	}
}
