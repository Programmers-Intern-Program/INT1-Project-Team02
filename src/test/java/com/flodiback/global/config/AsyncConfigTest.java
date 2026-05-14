package com.flodiback.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    void contextProgressExecutor_usesDedicatedThreadPrefix() {
        AsyncConfig config = new AsyncConfig();

        Executor executor = config.contextProgressExecutor();

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("context-progress-");
        taskExecutor.shutdown();
    }
}
