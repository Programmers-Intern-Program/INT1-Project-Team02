package com.flodiback.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("analysis-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "aiAnswerExecutor")
    public Executor aiAnswerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-answer-");
        executor.setRejectedExecutionHandler((runnable, poolExecutor) -> {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) poolExecutor;
            log.warn(
                    "AI answer task rejected. activeCount={}, queueSize={}",
                    pool.getActiveCount(),
                    pool.getQueue().size());
            throw new java.util.concurrent.RejectedExecutionException("AI answer task rejected");
        });
        executor.initialize();
        return executor;
    }

    @Bean(name = "contextProgressExecutor")
    public Executor contextProgressExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("context-progress-");
        executor.setRejectedExecutionHandler((runnable, poolExecutor) -> {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) poolExecutor;
            log.warn(
                    "Context progress task rejected. activeCount={}, queueSize={}",
                    pool.getActiveCount(),
                    pool.getQueue().size());
        });
        executor.initialize();
        return executor;
    }
}
