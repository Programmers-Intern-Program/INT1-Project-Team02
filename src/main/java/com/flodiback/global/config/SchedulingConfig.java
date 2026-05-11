package com.flodiback.global.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
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

    @Bean(name = "pendingScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService pendingScheduler() {
        AtomicInteger threadNo = new AtomicInteger(1);
        return Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("ai-pending-" + threadNo.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }
}
