package com.yourcompany.salesagent.mail.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.mail.infrastructure.*;
import com.yourcompany.salesagent.mail.api.*;
import com.yourcompany.salesagent.agent.infrastructure.AgentWorkflowMapper;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.interaction.application.ChatAnalysisService;
import com.yourcompany.salesagent.tool.email.EmailConfigurationService;

class MailConversationTests {
    private final UUID org=UUID.randomUUID(), member=UUID.randomUUID(), id=UUID.randomUUID();
    private final MailMapper mapper=mock(MailMapper.class);
    private final AgentWorkflowMapper agent=mock(AgentWorkflowMapper.class);
    private final ChatAnalysisService analysis=mock(ChatAnalysisService.class);
    private final MailConversationService service=new MailConversationService(mapper,mock(MailSyncService.class),agent,mock(FileStorageService.class),mock(EmailConfigurationService.class),analysis,Clock.systemUTC());
    private AuthPrincipal actor(String role) { return new AuthPrincipal(null,UUID.randomUUID(),org,member,"sales@test.local","Sales","Team",role,Instant.now().plusSeconds(900)); }
    @Test void salesCannotReadUnmatchedMailboxContents() {
        var row=new MailMessageRow(); when(mapper.message(org,id)).thenReturn(row);
        assertThatThrownBy(() -> service.get(actor("SALES"),id)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void readOnlyUsersCannotCreateReplyApprovalsOrInvokeModels() {
        assertThatThrownBy(() -> service.reply(actor("VIEWER"),id,new MailReplyRequest("reply","body",List.of(),0))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.analyze(actor("VIEWER"),id)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(agent,analysis);
    }
    @Test void salesCannotReadAnotherOwnersMail() {
        var row=new MailMessageRow(); var customer=new MailCustomerRow(); customer.setId(UUID.randomUUID()); customer.setOwnerMemberId(UUID.randomUUID()); row.setCustomerId(customer.getId());
        when(mapper.message(org,id)).thenReturn(row); when(mapper.customer(org,customer.getId())).thenReturn(customer);
        assertThatThrownBy(() -> service.get(actor("SALES"),id)).isInstanceOf(AccessDeniedException.class);
    }
}
