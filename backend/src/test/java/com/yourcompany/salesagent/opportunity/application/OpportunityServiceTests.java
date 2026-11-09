package com.yourcompany.salesagent.opportunity.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.opportunity.api.*;
import com.yourcompany.salesagent.opportunity.domain.*;
import com.yourcompany.salesagent.opportunity.infrastructure.*;

class OpportunityServiceTests {
	private final UUID org = UUID.randomUUID();
	private final UUID member = UUID.randomUUID();
	private final UUID id = UUID.randomUUID();
	private final UUID customer = UUID.randomUUID();
	private final Instant now = Instant.parse("2026-11-09T02:00:00Z");
	private final OpportunityMapper mapper = mock(OpportunityMapper.class);
	private final OpportunityService service = new OpportunityService(mapper, Clock.fixed(now, ZoneOffset.UTC));
	private AuthPrincipal actor() { return new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), org, member, "test@example.com", "销售", "团队", "OWNER", now.plusSeconds(3600)); }
	private OpportunityRow row() {
		var row = new OpportunityRow();
		row.setId(id); row.setCustomerId(customer); row.setStatus("OPEN"); row.setStage(OpportunityStage.QUALIFIED);
		row.setName("销售平台"); row.setVersion(2);
		return row;
	}
	private OpportunityUpsertRequest input() { return new OpportunityUpsertRequest(customer, "销售平台", member, BigDecimal.TEN, 20, null, ForecastCategory.PIPELINE, null, 2L); }
	@Test
	void refusesCrossTenantCustomerOnCreate() {
		assertThatThrownBy(() -> service.create(actor(), input())).isInstanceOf(OpportunityWorkflowException.class).hasMessageContaining("客户");
		verify(mapper, never()).insert(any(), any(), any(), any());
	}
	@Test
	void refusesInactiveOwnerOnCreate() {
		when(mapper.customerExists(org, customer)).thenReturn(1);
		assertThatThrownBy(() -> service.create(actor(), input())).isInstanceOf(OpportunityWorkflowException.class).hasMessageContaining("负责人");
	}
	@Test
	void missingOrForeignOpportunityIsNotFound() {
		assertThatThrownBy(() -> service.get(actor(), id)).isInstanceOf(ResponseStatusException.class);
		verify(mapper).selectOne(org, id);
	}
	@Test
	void requiresLostReasonBeforeWriting() {
		when(mapper.selectOne(org, id)).thenReturn(row());
		assertThatThrownBy(() -> service.changeStage(actor(), id, new OpportunityStageRequest(OpportunityStage.LOST, 2L, " ")))
				.isInstanceOf(OpportunityWorkflowException.class).hasMessageContaining("输单原因");
		verify(mapper, never()).changeStage(any(), any(), any(), anyInt(), any());
	}
	@Test
	void winningSetsProbabilityAndWritesHistory() {
		when(mapper.selectOne(org, id)).thenReturn(row());
		var request = new OpportunityStageRequest(OpportunityStage.WON, 2L, "合同确认");
		when(mapper.changeStage(org, id, request, 100, now)).thenReturn(1);
		service.changeStage(actor(), id, request);
		verify(mapper).insertEvent(eq(org), eq(id), eq(member), eq(OpportunityStage.QUALIFIED), eq(OpportunityStage.WON), contains("合同确认"), eq(now));
	}
	@Test
	void losingSetsZeroProbability() {
		when(mapper.selectOne(org, id)).thenReturn(row());
		var request = new OpportunityStageRequest(OpportunityStage.LOST, 2L, "预算取消");
		when(mapper.changeStage(org, id, request, 0, now)).thenReturn(1);
		service.changeStage(actor(), id, request);
		verify(mapper).changeStage(org, id, request, 0, now);
	}
	@Test
	void staleStageVersionDoesNotWriteHistory() {
		when(mapper.selectOne(org, id)).thenReturn(row());
		assertThatThrownBy(() -> service.changeStage(actor(), id, new OpportunityStageRequest(OpportunityStage.DEMO, 0L, null)))
				.isInstanceOf(OptimisticLockingFailureException.class);
		verify(mapper, never()).insertEvent(any(), any(), any(), any(), any(), any(), any());
	}
	@Test
	void closedOpportunityCannotBeModified() {
		var row = row(); row.setStatus("WON"); row.setStage(OpportunityStage.WON);
		when(mapper.selectOne(org, id)).thenReturn(row);
		assertThatThrownBy(() -> service.update(actor(), id, input())).isInstanceOf(OpportunityWorkflowException.class).hasMessageContaining("已关闭");
	}
	@Test
	void editingPreservesStageAndRecordsHistory() {
		when(mapper.selectOne(org, id)).thenReturn(row());
		when(mapper.customerExists(org, customer)).thenReturn(1);
		when(mapper.ownerExists(org, member)).thenReturn(1);
		var request = input();
		when(mapper.update(org, id, request, now)).thenReturn(1);
		service.update(actor(), id, request);
		verify(mapper).insertEvent(eq(org), eq(id), eq(member), eq(OpportunityStage.QUALIFIED), eq(OpportunityStage.QUALIFIED), contains("更新商机资料"), eq(now));
	}
}
