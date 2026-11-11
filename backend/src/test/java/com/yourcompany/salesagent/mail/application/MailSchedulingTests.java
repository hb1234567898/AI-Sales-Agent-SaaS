package com.yourcompany.salesagent.mail.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.yourcompany.salesagent.mail.infrastructure.MailMapper;

class MailSchedulingTests {
    private final ApplicationContextRunner context=new ApplicationContextRunner()
        .withUserConfiguration(MailSchedulingConfiguration.class,MailSyncScheduler.class)
        .withBean(MailMapper.class,()->mock(MailMapper.class))
        .withBean(MailSyncService.class,()->mock(MailSyncService.class))
        .withBean(Clock.class,Clock::systemUTC)
        .withPropertyValues("app.mail.sync-initial-delay-ms=600000");
    @Test void mailboxNetworkCallsHaveAnIndependentBoundedExecutor() {
        context.run(app -> {
            assertThat(app).hasNotFailed().hasSingleBean(MailSyncScheduler.class);
            var executor=app.getBean("mailSyncExecutor",ThreadPoolTaskExecutor.class);
            assertThat(executor.getMaxPoolSize()).isEqualTo(1);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
        });
    }
    @Test void scanningCanBeDisabledWithoutStartingWorkers() {
        context.withPropertyValues("app.mail.sync-enabled=false").run(app -> {
            assertThat(app).hasNotFailed().doesNotHaveBean(MailSyncScheduler.class).doesNotHaveBean("mailSyncExecutor");
        });
    }
}
