package com.flodiback.global.config;

import java.time.Clock;
import java.util.TimeZone;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class TimeConfig {

    private static final String DEFAULT_TIME_ZONE = "UTC";

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIME_ZONE));
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
