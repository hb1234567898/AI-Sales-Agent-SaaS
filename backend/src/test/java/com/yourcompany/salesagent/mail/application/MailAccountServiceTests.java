package com.yourcompany.salesagent.mail.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.mail.api.MailAccountRequest;
import com.yourcompany.salesagent.mail.infrastructure.*;
import com.yourcompany.salesagent.mail.transport.ImapMailboxClient;
import com.yourcompany.salesagent.shared.security.SecretCipher;

class MailAccountServiceTests {
    private final UUID org=UUID.randomUUID(); private final MailMapper mapper=mock(MailMapper.class);
    private final SecretCipher cipher=mock(SecretCipher.class); private final ImapMailboxClient transport=mock(ImapMailboxClient.class);
    private final MailAccountService service=new MailAccountService(mapper,cipher,transport,Clock.systemUTC());
    private AuthPrincipal actor(String role) { return new AuthPrincipal(null,UUID.randomUUID(),org,UUID.randomUUID(),"admin@test.local","Admin","Team",role,Instant.now().plusSeconds(900)); }
    @Test void membersCannotChangeSharedCredentials() {
        assertThatThrownBy(() -> service.save(actor("SALES"),new MailAccountRequest("imap.test.local",993,"a@test.local","secret",true,"INBOX",true,-1))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(mapper,cipher,transport);
    }
    @Test void changingServerNeverReusesTheOldServersPassword() {
        var old=new MailAccountRow(); old.setHost("imap.old.test"); old.setUsername("a@test.local"); old.setEncryptedPassword("encrypted"); when(mapper.account(org)).thenReturn(old);
        assertThatThrownBy(() -> service.save(actor("OWNER"),new MailAccountRequest("imap.new.test",993,"a@test.local",null,true,"INBOX",true,0))).isInstanceOf(MailWorkflowException.class);
        verifyNoInteractions(cipher,transport);
    }
    @Test void statusNeverReturnsStoredCiphertextOrPlainPassword() {
        var old=new MailAccountRow(); old.setHost("imap.test.local"); old.setUsername("a@test.local"); old.setEncryptedPassword("sensitive-ciphertext"); when(mapper.account(org)).thenReturn(old);
        var status=service.status(org); assertThat(status.passwordConfigured()).isTrue(); assertThat(status.toString()).doesNotContain("sensitive-ciphertext"); verifyNoInteractions(cipher);
    }
}
