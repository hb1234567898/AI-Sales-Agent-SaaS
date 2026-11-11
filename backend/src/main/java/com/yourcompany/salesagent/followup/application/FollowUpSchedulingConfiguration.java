package com.yourcompany.salesagent.followup.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.follow-up.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class FollowUpSchedulingConfiguration {
    @Bean("followUpExecutor")
    ThreadPoolTaskExecutor followUpExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("follow-up-ai-");
        return executor;
    }
}
