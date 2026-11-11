package com.yourcompany.salesagent.mail.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.mail.sync-enabled",havingValue="true",matchIfMissing=true)
public class MailSchedulingConfiguration {
    @Bean("mailSyncExecutor")
    ThreadPoolTaskExecutor mailSyncExecutor() {
        var executor=new ThreadPoolTaskExecutor(); executor.setCorePoolSize(1); executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0); executor.setThreadNamePrefix("mail-sync-"); executor.setAwaitTerminationSeconds(15);
        return executor;
    }
}
