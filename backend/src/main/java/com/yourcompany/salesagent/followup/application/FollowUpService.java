package com.yourcompany.salesagent.followup.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.followup.api.FollowUpResponse;
import com.yourcompany.salesagent.followup.infrastructure.FollowUpMapper;

@Service
public class FollowUpService {

	private final FollowUpMapper mapper;
	private final Clock clock;
	private final UUID organizationId;

	public FollowUpService(
			FollowUpMapper mapper,
			Clock clock,
			@Value("${app.demo.organization-id}") UUID organizationId) {
		this.mapper = mapper;
		this.clock = clock;
		this.organizationId = organizationId;
	}

	@Transactional(readOnly = true)
	public IPage<FollowUpResponse> findFollowUps(String filter, int page, int size) {
		var rows = mapper.selectFollowUps(Page.of(page + 1L, size), organizationId, normalizeFilter(filter), clock.instant());
		return new Page<FollowUpResponse>(rows.getCurrent(), rows.getSize(), rows.getTotal())
				.setRecords(rows.getRecords().stream().map(FollowUpResponse::from).toList());
	}

	@Transactional
	public FollowUpResponse complete(AuthPrincipal principal, UUID followUpId) {
		var updated = mapper.completeFollowUp(organizationId, followUpId, principal.memberId(), clock.instant());
		if (updated == 0) {
			throw new FollowUpWorkflowException("跟进任务不存在、已完成或无权处理");
		}
		return requireFollowUp(followUpId);
	}

	private FollowUpResponse requireFollowUp(UUID followUpId) {
		var row = mapper.selectFollowUp(organizationId, followUpId);
		if (row == null) {
			throw new FollowUpWorkflowException("跟进任务不存在");
		}
		return FollowUpResponse.from(row);
	}

	private static String normalizeFilter(String filter) {
		return filter == null || filter.isBlank() ? "ALL" : filter.strip().toUpperCase();
	}
}
