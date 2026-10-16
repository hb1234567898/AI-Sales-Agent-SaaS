package com.yourcompany.salesagent.lead.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.customer.api.OwnerOptionResponse;
import com.yourcompany.salesagent.lead.domain.LeadStatus;

public interface LeadMapper {
	IPage<LeadRow> selectLeads(Page<LeadRow> page, @Param("organizationId") UUID organizationId,
			@Param("query") String query, @Param("status") LeadStatus status);
	LeadRow selectLead(@Param("organizationId") UUID organizationId, @Param("leadId") UUID leadId);
	LeadMetricsRow selectMetrics(@Param("organizationId") UUID organizationId);
	List<OwnerOptionResponse> selectOwners(@Param("organizationId") UUID organizationId);
	int ownerExists(@Param("organizationId") UUID organizationId, @Param("memberId") UUID memberId);
	int countDuplicates(@Param("organizationId") UUID organizationId, @Param("excludeId") UUID excludeId,
			@Param("company") String company, @Param("email") String email, @Param("phone") String phone);
	int insertLead(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
			@Param("company") String company, @Param("industry") String industry,
			@Param("source") CustomerSource source, @Param("status") LeadStatus status,
			@Param("ownerMemberId") UUID ownerMemberId, @Param("score") Integer score,
			@Param("nextAction") String nextAction, @Param("nextFollowUpAt") Instant nextFollowUpAt,
			@Param("now") Instant now);
	int insertContact(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
			@Param("leadId") UUID leadId, @Param("name") String name, @Param("email") String email,
			@Param("phone") String phone, @Param("title") String title, @Param("source") CustomerSource source,
			@Param("now") Instant now);
	int updateLead(@Param("organizationId") UUID organizationId, @Param("leadId") UUID leadId,
			@Param("company") String company, @Param("industry") String industry,
			@Param("source") CustomerSource source, @Param("status") LeadStatus status,
			@Param("ownerMemberId") UUID ownerMemberId, @Param("score") Integer score,
			@Param("nextAction") String nextAction, @Param("nextFollowUpAt") Instant nextFollowUpAt,
			@Param("now") Instant now);
	int updateContact(@Param("organizationId") UUID organizationId, @Param("leadId") UUID leadId,
			@Param("name") String name, @Param("email") String email, @Param("phone") String phone,
			@Param("title") String title, @Param("now") Instant now);
	int assignLead(@Param("organizationId") UUID organizationId, @Param("leadId") UUID leadId,
			@Param("ownerMemberId") UUID ownerMemberId, @Param("now") Instant now);
	int convertLead(@Param("organizationId") UUID organizationId, @Param("leadId") UUID leadId,
			@Param("now") Instant now);
	int insertOpportunity(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
			@Param("customerId") UUID customerId, @Param("ownerMemberId") UUID ownerMemberId,
			@Param("name") String name, @Param("amount") BigDecimal amount,
			@Param("expectedCloseDate") LocalDate expectedCloseDate, @Param("now") Instant now);
}
