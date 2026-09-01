package com.yourcompany.salesagent.approval.infrastructure;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface ApprovalMapper {

	IPage<ApprovalRow> selectApprovals(Page<ApprovalRow> page, @Param("organizationId") UUID organizationId, @Param("status") String status);

	ApprovalRow selectApproval(@Param("organizationId") UUID organizationId, @Param("approvalId") UUID approvalId);

	int updateDecision(
			@Param("organizationId") UUID organizationId,
			@Param("approvalId") UUID approvalId,
			@Param("deciderMemberId") UUID deciderMemberId,
			@Param("status") String status,
			@Param("comment") String comment,
			@Param("expectedVersion") Long expectedVersion,
			@Param("now") Instant now);

	int insertFollowUpFromApprovedAction(
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId,
			@Param("now") Instant now);

	int markActionSucceeded(@Param("organizationId") UUID organizationId, @Param("actionRequestId") UUID actionRequestId, @Param("now") Instant now);

	int markActionRejected(@Param("organizationId") UUID organizationId, @Param("actionRequestId") UUID actionRequestId, @Param("now") Instant now);
}
