package com.yourcompany.salesagent.opportunity.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.opportunity.api.*;
import com.yourcompany.salesagent.opportunity.domain.OpportunityStage;

public interface OpportunityMapper {
	IPage<OpportunityRow> selectPage(Page<OpportunityRow> page, @Param("org") UUID org,
			@Param("query") String query, @Param("stage") OpportunityStage stage, @Param("owner") UUID owner);
	OpportunityRow selectOne(@Param("org") UUID org, @Param("id") UUID id);
	OpportunityMetricsResponse selectMetrics(@Param("org") UUID org, @Param("now") Instant now);
	int customerExists(@Param("org") UUID org, @Param("id") UUID id);
	int ownerExists(@Param("org") UUID org, @Param("id") UUID id);
	int insert(@Param("org") UUID org, @Param("id") UUID id,
			@Param("input") OpportunityUpsertRequest input, @Param("now") Instant now);
	int update(@Param("org") UUID org, @Param("id") UUID id,
			@Param("input") OpportunityUpsertRequest input, @Param("now") Instant now);
	int changeStage(@Param("org") UUID org, @Param("id") UUID id,
			@Param("input") OpportunityStageRequest input, @Param("probability") int probability, @Param("now") Instant now);
	int insertEvent(@Param("org") UUID org, @Param("id") UUID id, @Param("actor") UUID actor,
			@Param("from") OpportunityStage from, @Param("to") OpportunityStage to,
			@Param("summary") String summary, @Param("now") Instant now);
	List<OpportunityEventResponse> selectEvents(@Param("org") UUID org, @Param("id") UUID id);
}
