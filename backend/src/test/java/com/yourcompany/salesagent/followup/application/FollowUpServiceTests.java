package com.yourcompany.salesagent.followup.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.followup.api.*;
import com.yourcompany.salesagent.followup.infrastructure.*;

class FollowUpServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-11T16:30:00Z");
    private final UUID org = UUID.randomUUID(), member = UUID.randomUUID(), id = UUID.randomUUID();
    private final FollowUpMapper mapper = mock(FollowUpMapper.class);
    private final FollowUpService service = new FollowUpService(mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    private AuthPrincipal actor(String role) {
        return new AuthPrincipal(null, UUID.randomUUID(), org, member, "sales@test.local", "Sales", "Team", role, NOW.plusSeconds(900));
    }
    private FollowUpRow task(String status) {
        var row = new FollowUpRow(); row.setId(id); row.setOwnerMemberId(member); row.setStatus(status); row.setVersion(3);
        when(mapper.selectFollowUp(org, id)).thenReturn(row); return row;
    }
    @Test void metricsUseShanghaiDayAndAuthenticatedTenant() {
        service.metrics(actor("SALES"), "MINE");
        verify(mapper).selectMetrics(org, member, NOW, Instant.parse("2026-11-11T16:00:00Z"), Instant.parse("2026-11-12T16:00:00Z"));
        service.metrics(actor("MANAGER"), "TEAM");
        verify(mapper).selectMetrics(eq(org), isNull(), eq(NOW), any(), any());
    }
    @Test void salesCannotRequestTeamScopeOrAnotherOwnersTask() {
        assertThatThrownBy(() -> service.metrics(actor("SALES"), "TEAM")).isInstanceOf(AccessDeniedException.class);
        task("OPEN").setOwnerMemberId(UUID.randomUUID());
        assertThatThrownBy(() -> service.get(actor("SALES"), id)).isInstanceOf(AccessDeniedException.class);
        verify(mapper, never()).selectMetrics(any(), any(), any(), any(), any());
    }
    @Test void completionUsesVersionAndRejectsConflicts() {
        task("OPEN");
        assertThatThrownBy(() -> service.complete(actor("SALES"), id, 2L)).isInstanceOf(OptimisticLockingFailureException.class);
        verify(mapper).changeStatus(org, id, 2L, "COMPLETED", NOW);
    }
    @Test void legacyCompletionStillUsesOptimisticVersion() {
        task("OPEN"); when(mapper.changeStatus(org, id, 3L, "COMPLETED", NOW)).thenReturn(1);
        service.complete(actor("SALES"), id, null);
        verify(mapper).changeStatus(org, id, 3L, "COMPLETED", NOW);
    }
    @Test void closedTasksAndReadOnlyAccountsCannotBeChanged() {
        task("COMPLETED");
        assertThatThrownBy(() -> service.cancel(actor("SALES"), id, 3L)).isInstanceOf(FollowUpWorkflowException.class);
        assertThatThrownBy(() -> service.complete(actor("VIEWER"), id, 3L)).isInstanceOf(AccessDeniedException.class);
        verify(mapper, never()).changeStatus(any(), any(), anyLong(), any(), any());
    }
    @Test void snoozeMustBeInFuture() {
        task("OPEN");
        assertThatThrownBy(() -> service.snooze(actor("SALES"), id, new FollowUpSnoozeRequest(NOW, "Later", 3L)))
                .isInstanceOf(FollowUpWorkflowException.class);
        verify(mapper, never()).snooze(any(), any(), any(), any());
    }
    @Test void cannotAssignTaskAcrossOwnersOrTenants() {
        var input = new FollowUpUpsertRequest(UUID.randomUUID(), UUID.randomUUID(), "Call", NOW, 60, "", "CALL", null);
        assertThatThrownBy(() -> service.create(actor("SALES"), input)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(actor("OWNER"), input)).isInstanceOf(FollowUpWorkflowException.class);
        verify(mapper, never()).insert(any(), any(), any(), any());
    }
}
