package com.yourcompany.salesagent.followup.infrastructure;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface FollowUpMapper {

	IPage<FollowUpRow> selectFollowUps(
			Page<FollowUpRow> page,
			@Param("organizationId") UUID organizationId,
			@Param("filter") String filter,
			@Param("now") Instant now);

	FollowUpRow selectFollowUp(@Param("organizationId") UUID organizationId, @Param("followUpId") UUID followUpId);

	int completeFollowUp(
			@Param("organizationId") UUID organizationId,
			@Param("followUpId") UUID followUpId,
			@Param("memberId") UUID memberId,
			@Param("now") Instant now);
}
