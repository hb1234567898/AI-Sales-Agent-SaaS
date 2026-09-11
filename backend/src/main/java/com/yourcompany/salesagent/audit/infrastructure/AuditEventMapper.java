package com.yourcompany.salesagent.audit.infrastructure;

import java.util.UUID;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.audit.domain.AuditEvent;

public interface AuditEventMapper extends BaseMapper<AuditEvent> {

	int insertAuditEvent(AuditEvent event);

	IPage<AuditEvent> selectAuditEvents(
			Page<AuditEvent> page,
			@Param("organizationId") UUID organizationId,
			@Param("keyword") String keyword,
			@Param("action") String action,
			@Param("targetType") String targetType,
			@Param("result") String result);
}
