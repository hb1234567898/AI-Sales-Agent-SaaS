package com.yourcompany.salesagent.interaction.domain;

public enum ChatPlatform {
	WECHAT("微信"),
	WHATSAPP("WhatsApp"),
	OTHER("其他聊天工具");

	private final String displayName;

	ChatPlatform(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
