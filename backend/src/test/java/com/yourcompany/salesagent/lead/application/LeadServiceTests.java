package com.yourcompany.salesagent.lead.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.lead.api.LeadConvertRequest;
import com.yourcompany.salesagent.lead.api.LeadUpsertRequest;
import com.yourcompany.salesagent.lead.domain.LeadStatus;
import com.yourcompany.salesagent.lead.infrastructure.LeadMapper;
import com.yourcompany.salesagent.lead.infrastructure.LeadRow;

class LeadServiceTests {
	private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID LEAD_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final Instant NOW = Instant.parse("2026-09-22T02:00:00Z");

	@Test
	void rejectsDuplicateLead() {
		var mapper = mock(LeadMapper.class);
		when(mapper.countDuplicates(eq(ORGANIZATION_ID), eq(null), eq("云岚科技"), eq("sales@example.com"), eq("13800000000")))
				.thenReturn(1);

		assertThatThrownBy(() -> service(mapper).createLead(principal(), request()))
				.isInstanceOf(LeadWorkflowException.class)
				.hasMessageContaining("相同企业");
	}

	@Test
	void requiresOwnerBeforeConversion() {
		var mapper = mock(LeadMapper.class);
		when(mapper.selectLead(ORGANIZATION_ID, LEAD_ID)).thenReturn(row(null));

		assertThatThrownBy(() -> service(mapper).convert(principal(), LEAD_ID,
				new LeadConvertRequest("云岚科技商机", BigDecimal.TEN, LocalDate.parse("2026-10-01"))))
				.isInstanceOf(LeadWorkflowException.class)
				.hasMessageContaining("分配负责人");
	}

	@Test
	void convertsLeadAndCreatesOpportunityInOneServiceCall() {
		var mapper = mock(LeadMapper.class);
		when(mapper.selectLead(ORGANIZATION_ID, LEAD_ID)).thenReturn(row(MEMBER_ID));
		when(mapper.convertLead(ORGANIZATION_ID, LEAD_ID, NOW)).thenReturn(1);
		var request = new LeadConvertRequest("云岚科技商机", new BigDecimal("200000"), LocalDate.parse("2026-10-31"));

		var result = service(mapper).convert(principal(), LEAD_ID, request);

		assertThat(result.customerId()).isEqualTo(LEAD_ID);
		verify(mapper).insertOpportunity(any(UUID.class), eq(ORGANIZATION_ID), eq(LEAD_ID), eq(MEMBER_ID),
				eq("云岚科技商机"), eq(new BigDecimal("200000")), eq(LocalDate.parse("2026-10-31")), eq(NOW));
	}

	private static LeadService service(LeadMapper mapper) {
		return new LeadService(mapper, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static AuthPrincipal principal() {
		return new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), ORGANIZATION_ID, MEMBER_ID,
				"owner@example.com", "陈默", "演示销售团队", "OWNER", NOW.plusSeconds(3600));
	}

	private static LeadUpsertRequest request() {
		return new LeadUpsertRequest("云岚科技", "企业服务", "苏恬", "sales@example.com", "13800000000",
				"采购经理", CustomerSource.MANUAL, LeadStatus.NEW, null, 80, "首次联系", null);
	}

	private static LeadRow row(UUID ownerId) {
		var row = new LeadRow();
		row.setId(LEAD_ID); row.setCompany("云岚科技"); row.setContactName("苏恬"); row.setSource(CustomerSource.MANUAL);
		row.setStatus(LeadStatus.NEW); row.setOwnerMemberId(ownerId); row.setCreatedAt(NOW); row.setUpdatedAt(NOW);
		return row;
	}
}
