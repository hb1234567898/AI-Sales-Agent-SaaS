package com.yourcompany.salesagent.mail.application;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.yourcompany.salesagent.mail.infrastructure.MailMapper;

@Component
@ConditionalOnProperty(name="app.mail.sync-enabled",havingValue="true",matchIfMissing=true)
public class MailSyncScheduler {
    private final MailMapper mapper; private final MailSyncService service; private final Clock clock; private final Executor executor;
    public MailSyncScheduler(MailMapper mapper, MailSyncService service, Clock clock, @Qualifier("mailSyncExecutor") Executor executor) { this.mapper=mapper; this.service=service; this.clock=clock; this.executor=executor; }
    @Scheduled(fixedDelayString="${app.mail.sync-interval-ms:60000}",initialDelayString="${app.mail.sync-initial-delay-ms:20000}")
    public void scan() {
        try { executor.execute(this::scanMailboxes); }
        catch(TaskRejectedException ignored) { /* The previous mailbox batch is still running. */ }
    }
    private void scanMailboxes() {
        try { mapper.expireSending(clock.instant()); for(var org:mapper.dueAccounts(clock.instant())) { try { service.sync(org); } catch(RuntimeException ignored) { /* Error is persisted for the mailbox owner. */ } } }
        catch(RuntimeException error) { LoggerFactory.getLogger(getClass()).warn("Mailbox scan failed ({})",error.getClass().getSimpleName()); }
    }
}
