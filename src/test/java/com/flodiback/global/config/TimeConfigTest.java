package com.flodiback.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

class TimeConfigTest {

    private final TimeConfig timeConfig = new TimeConfig();

    @Test
    void clock_usesUtc() {
        assertThat(timeConfig.clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void setDefaultTimeZone_setsUtc() {
        timeConfig.setDefaultTimeZone();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }
}
