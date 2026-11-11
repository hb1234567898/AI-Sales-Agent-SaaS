package com.yourcompany.salesagent.mail.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import com.yourcompany.salesagent.mail.infrastructure.*;
import com.yourcompany.salesagent.mail.transport.*;

class MailSyncServiceTests {
    private final UUID org=UUID.randomUUID();
    private final MailMapper mapper=mock(MailMapper.class);
    private final MailSyncService service=new MailSyncService(mapper,mock(MailAccountService.class),mock(ImapMailboxClient.class),mock(PlatformTransactionManager.class),Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"),ZoneOffset.UTC));
    private InboundMail input(boolean automatic) { return new InboundMail("<reply@example.test>","buyer@example.test",List.of("sales@example.test"),"Re: Quote","Confirm budget",List.of(),List.of(),Instant.parse("2026-11-11T02:00:00Z"),automatic); }
    private MailCustomerRow customer() { var c=new MailCustomerRow(); c.setId(UUID.randomUUID()); c.setOwnerMemberId(UUID.randomUUID()); return c; }
    @Test void duplicatesDoNotCreateRepeatedInteractionsOrTasks() {
        when(mapper.customersByEmail(org,"buyer@example.test")).thenReturn(List.of(customer()));
        assertThat(service.importMessage(org,input(false))).isEqualTo(-1);
        verify(mapper,never()).insertInteraction(any()); verify(mapper,never()).createTask(any(),any());
    }
    @Test void uniqueCustomerCreatesOneInteractionAndOneTask() {
        when(mapper.customersByEmail(org,"buyer@example.test")).thenReturn(List.of(customer())); when(mapper.insertMessage(any())).thenReturn(1);
        assertThat(service.importMessage(org,input(false))).isEqualTo(1);
        verify(mapper).insertInteraction(argThat(r -> r.getOrganizationId().equals(org) && r.getCustomerId()!=null && r.getInteractionId()!=null));
        verify(mapper).createTask(any(),any());
    }
    @Test void ambiguousEmailNeverGetsAssignedToAnArbitraryCustomer() {
        when(mapper.customersByEmail(org,"buyer@example.test")).thenReturn(List.of(customer(),customer())); when(mapper.insertMessage(any())).thenReturn(1);
        assertThat(service.importMessage(org,input(false))).isZero();
        verify(mapper).insertMessage(argThat(r -> r.getStatus().equals("UNMATCHED") && r.getCustomerId()==null));
        verify(mapper,never()).createTask(any(),any());
    }
    @Test void autoRepliesAndDeliveryReportsNeverTriggerTasks() {
        when(mapper.insertMessage(any())).thenReturn(1); service.importMessage(org,input(true));
        verify(mapper).insertMessage(argThat(r -> r.getStatus().equals("IGNORED")));
        verify(mapper,never()).customersByEmail(any(),any()); verify(mapper,never()).createTask(any(),any());
    }
}
