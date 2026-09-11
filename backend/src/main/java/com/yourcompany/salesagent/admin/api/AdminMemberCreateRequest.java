package com.yourcompany.salesagent.admin.api;

import com.yourcompany.salesagent.admin.domain.MemberRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminMemberCreateRequest(
		@NotBlank(message = "请输入成员姓名")
		@Size(max = 120, message = "成员姓名不能超过 120 个字符")
		String displayName,
		@NotBlank(message = "请输入成员邮箱")
		@Email(message = "请输入有效的邮箱地址")
		@Size(max = 320, message = "邮箱不能超过 320 个字符")
		String email,
		@NotNull(message = "请选择成员角色")
		MemberRole role,
		@NotBlank(message = "请输入初始密码")
		@Size(min = 10, max = 72, message = "初始密码长度需为 10 到 72 个字符")
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
				message = "初始密码必须同时包含大写字母、小写字母和数字")
		String initialPassword) {
}
