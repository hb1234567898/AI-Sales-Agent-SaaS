package com.yourcompany.salesagent.followup.application;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.follow-up.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class FollowUpScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(FollowUpScheduler.class);
    private final FollowUpAutomationService service;
    private final Executor executor;
    public FollowUpScheduler(FollowUpAutomationService service, @Qualifier("followUpExecutor") Executor executor) {
        this.service = service; this.executor = executor;
    }
    @Scheduled(fixedDelayString = "${app.follow-up.scan-interval-ms:60000}", initialDelayString = "${app.follow-up.initial-delay-ms:10000}")
    public void scan() {
        try { service.scan(); }
        catch (RuntimeException error) { LOG.warn("Proactive follow-up scan failed; will retry on next tick", error); }
        for (var batch : service.queuedRuns()) {
            try { executor.execute(() -> service.process(batch)); }
            catch (TaskRejectedException error) { break; }
        }
    }
}
