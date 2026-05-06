package com.flodiback.global.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskExecutor rollingSummaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rolling-summary-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler((runnable, poolExecutor) -> {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) poolExecutor;
            log.warn(
                    "Rolling summary task rejected. activeCount={}, queueSize={}",
                    pool.getActiveCount(),
                    pool.getQueue().size());
            throw new java.util.concurrent.RejectedExecutionException("Rolling summary task rejected");
        });
        executor.initialize();
        return executor;
    }
}
