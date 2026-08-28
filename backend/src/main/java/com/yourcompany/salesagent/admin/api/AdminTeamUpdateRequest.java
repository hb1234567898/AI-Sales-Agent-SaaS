package com.yourcompany.salesagent.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminTeamUpdateRequest(
		@NotBlank(message = "请输入团队名称")
		@Size(max = 200, message = "团队名称不能超过 200 个字符")
		String name,
		@NotBlank(message = "请选择团队时区")
		@Size(max = 64, message = "时区不能超过 64 个字符")
		String timezone,
		@NotBlank(message = "请选择界面语言")
		@Pattern(regexp = "^(zh-CN|en-US)$", message = "暂时只支持简体中文或英文")
		String locale) {
}
