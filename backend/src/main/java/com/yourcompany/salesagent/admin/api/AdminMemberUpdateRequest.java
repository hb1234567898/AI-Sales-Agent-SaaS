package com.yourcompany.salesagent.admin.api;

import com.yourcompany.salesagent.admin.domain.MemberRole;
import com.yourcompany.salesagent.admin.domain.MemberStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminMemberUpdateRequest(
		@NotBlank(message = "请输入成员姓名")
		@Size(max = 120, message = "成员姓名不能超过 120 个字符")
		String displayName,
		@NotNull(message = "请选择成员角色")
		MemberRole role,
		@NotNull(message = "请选择成员状态")
		MemberStatus status) {
}
