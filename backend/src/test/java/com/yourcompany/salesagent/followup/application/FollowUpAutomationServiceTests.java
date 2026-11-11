package com.yourcompany.salesagent.followup.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.yourcompany.salesagent.agent.api.AgentRunResponse;
import com.yourcompany.salesagent.agent.application.SalesFollowUpAgentService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.followup.infrastructure.*;

class FollowUpAutomationServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-11T16:30:00Z");
    private final UUID org = UUID.randomUUID(), member = UUID.randomUUID(), batchId = UUID.randomUUID();
    private final FollowUpAutomationMapper mapper = mock(FollowUpAutomationMapper.class);
    private final SalesFollowUpAgentService agent = mock(SalesFollowUpAgentService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final FollowUpAutomationService service = new FollowUpAutomationService(mapper, agent, transactions, Clock.fixed(NOW, ZoneOffset.UTC));
    private AuthPrincipal actor() { return new AuthPrincipal(null, UUID.randomUUID(), org, member, "a@b.test", "Sales", "Org", "SALES", NOW.plusSeconds(900)); }
    private FollowUpRuleRow rule() {
        var row = new FollowUpRuleRow(); row.setId(UUID.randomUUID()); row.setOrganizationId(org); row.setMemberId(member);
        row.setEnabled(true); row.setAutoAnalyze(true); return row;
    }
    private FollowUpAutomationRunRow batch() {
        var row = new FollowUpAutomationRunRow(); row.setId(batchId); row.setOrganizationId(org); row.setMemberId(member); return row;
    }
    @Test void newUsersDoNotAutomaticallyConsumeTokens() {
        var rule = service.rule(actor());
        assertThat(rule.enabled()).isFalse(); assertThat(rule.autoAnalyze()).isFalse(); assertThat(rule.version()).isEqualTo(-1);
        verifyNoInteractions(agent);
    }
    @Test void repeatedDailyCheckDoesNotCreateTasksAgain() {
        var rule = rule(); when(mapper.lockRule(org, member)).thenReturn(rule);
        when(mapper.selectActor(org, member)).thenReturn(new FollowUpActorRow());
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service.runNow(actor());
        verify(mapper).insertRun(any(), eq(rule), eq(LocalDate.parse("2026-11-12")), eq(NOW));
        verify(mapper, never()).createTasks(any(), any(), any(), any());
        verifyNoInteractions(agent);
        verify(transactions).commit(any());
    }
    @Test void firstDailyCheckQueuesAnalysisOnlyForNewTasks() {
        var rule = rule(); when(mapper.lockRule(org, member)).thenReturn(rule);
        when(mapper.selectActor(org, member)).thenReturn(new FollowUpActorRow());
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mapper.insertRun(any(), eq(rule), any(), eq(NOW))).thenReturn(1);
        when(mapper.createTasks(any(), eq(rule), eq(NOW), any())).thenReturn(2);
        service.runNow(actor());
        verify(mapper).prepared(eq(org), any(), eq(2), eq("QUEUED"), eq(NOW));
        verifyNoInteractions(agent);
    }
    @Test void duplicateWorkersCannotExecuteSameBatch() {
        service.process(batch());
        verifyNoInteractions(agent); verify(mapper, never()).selectRunCustomers(any(), any());
    }
    @Test void queuedAnalysisIsCancelledAfterRuleIsDisabled() {
        when(mapper.claimRun(org, batchId, NOW)).thenReturn(1);
        var rule = rule(); rule.setEnabled(false); when(mapper.selectRule(org, member)).thenReturn(rule);
        service.process(batch());
        verify(mapper).finishRun(eq(org), eq(batchId), eq("CANCELLED"), isNull(), anyString(), eq(NOW));
        verifyNoInteractions(agent);
    }
    @Test void queuedAnalysisUsesMemberTenantAndLinksAgentHistory() {
        when(mapper.claimRun(org, batchId, NOW)).thenReturn(1);
        when(mapper.selectRule(org, member)).thenReturn(rule());
        var actor = new FollowUpActorRow(); actor.setOrganizationId(org); actor.setMemberId(member); actor.setRole("SALES");
        when(mapper.selectActor(org, member)).thenReturn(actor);
        var customer = UUID.randomUUID(); when(mapper.selectRunCustomers(org, batchId)).thenReturn(List.of(customer));
        var result = mock(AgentRunResponse.class); var agentId = UUID.randomUUID(); when(result.id()).thenReturn(agentId);
        when(agent.runScheduled(any(), any(), eq(batchId))).thenReturn(result);
        service.process(batch());
        verify(agent).runScheduled(argThat(p -> p.organizationId().equals(org) && p.memberId().equals(member)),
                argThat(r -> r.customerIds().equals(List.of(customer))), eq(batchId));
        verify(mapper).finishRun(org, batchId, "COMPLETED", agentId, null, NOW);
    }
    @Test void noLongerEligibleCustomersDoNotCallModel() {
        when(mapper.claimRun(org, batchId, NOW)).thenReturn(1);
        when(mapper.selectRule(org, member)).thenReturn(rule()); when(mapper.selectActor(org, member)).thenReturn(new FollowUpActorRow());
        when(mapper.selectRunCustomers(org, batchId)).thenReturn(List.of());
        service.process(batch());
        verifyNoInteractions(agent); verify(mapper).finishRun(org, batchId, "COMPLETED", null, null, NOW);
    }
}
