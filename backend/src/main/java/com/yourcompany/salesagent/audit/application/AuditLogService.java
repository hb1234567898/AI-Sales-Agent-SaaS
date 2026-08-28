package com.yourcompany.salesagent.audit.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.audit.api.AuditEventResponse;
import com.yourcompany.salesagent.audit.domain.AuditEvent;
import com.yourcompany.salesagent.audit.infrastructure.AuditEventMapper;

@Service
public class AuditLogService {

	private final AuditEventMapper auditEventMapper;

	public AuditLogService(AuditEventMapper auditEventMapper) {
		this.auditEventMapper = auditEventMapper;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(AuditLogCommand command) {
		auditEventMapper.insertAuditEvent(AuditEvent.create(
				command.organizationId(),
				command.actorMemberId(),
				command.actorIdentifier(),
				command.action(),
				command.targetType(),
				command.targetId(),
				command.result(),
				command.ipAddress(),
				command.userAgent(),
				command.requestId(),
				command.metadata(),
				command.occurredAt()));
	}

	@Transactional(readOnly = true)
	public IPage<AuditEventResponse> findAuditEvents(
			UUID organizationId,
			String keyword,
			String action,
			String targetType,
			String result,
			int page,
			int size) {
		var rows = auditEventMapper.selectAuditEvents(
				Page.of(page + 1L, size),
				organizationId,
				blankToNull(keyword),
				blankToNull(action),
				blankToNull(targetType),
				blankToNull(result));
		var records = rows.getRecords().stream().map(AuditEventResponse::from).toList();
		return new Page<AuditEventResponse>(rows.getCurrent(), rows.getSize(), rows.getTotal())
				.setRecords(records);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
