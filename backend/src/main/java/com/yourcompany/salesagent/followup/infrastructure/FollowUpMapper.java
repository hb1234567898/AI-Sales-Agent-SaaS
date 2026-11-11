package com.yourcompany.salesagent.followup.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.followup.api.*;

public interface FollowUpMapper {
    IPage<FollowUpRow> selectFollowUps(Page<FollowUpRow> page, @Param("organizationId") UUID organizationId,
            @Param("owner") UUID owner, @Param("filter") String filter, @Param("now") Instant now,
            @Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);
    FollowUpRow selectFollowUp(@Param("organizationId") UUID organizationId, @Param("followUpId") UUID followUpId);
    FollowUpMetricsResponse selectMetrics(@Param("organizationId") UUID organizationId, @Param("owner") UUID owner,
            @Param("now") Instant now, @Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);
    int customerExists(@Param("organizationId") UUID organizationId, @Param("id") UUID id, @Param("owner") UUID owner);
    int ownerExists(@Param("organizationId") UUID organizationId, @Param("id") UUID id);
    List<FollowUpChoiceRow> selectCustomers(@Param("organizationId") UUID organizationId, @Param("query") String query,
            @Param("owner") UUID owner);
    List<FollowUpChoiceRow> selectOwners(@Param("organizationId") UUID organizationId, @Param("owner") UUID owner);
    int insert(@Param("organizationId") UUID organizationId, @Param("id") UUID id,
            @Param("input") FollowUpUpsertRequest input, @Param("now") Instant now);
    int update(@Param("organizationId") UUID organizationId, @Param("id") UUID id,
            @Param("input") FollowUpUpsertRequest input, @Param("now") Instant now);
    int changeStatus(@Param("organizationId") UUID organizationId, @Param("id") UUID id, @Param("version") long version,
            @Param("status") String status, @Param("now") Instant now);
    int snooze(@Param("organizationId") UUID organizationId, @Param("id") UUID id,
            @Param("input") FollowUpSnoozeRequest input, @Param("now") Instant now);
}
