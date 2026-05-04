package com.flodiback.domain.meeting.meetinglog.rolling;

import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollingSummaryStreamInitializer implements ApplicationRunner {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(RollingSummaryStreamConstants.STREAM_KEY))) {
                redisTemplate
                        .opsForStream()
                        .add(StreamRecords.newRecord()
                                .ofMap(Map.of("type", "init"))
                                .withStreamKey(RollingSummaryStreamConstants.STREAM_KEY));
            }
            redisTemplate
                    .opsForStream()
                    .createGroup(
                            RollingSummaryStreamConstants.STREAM_KEY,
                            ReadOffset.latest(),
                            RollingSummaryStreamConstants.GROUP_NAME);
        } catch (DataAccessException e) {
            String message = e.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                log.debug("Rolling summary stream group already exists.");
                return;
            }
            log.warn("Rolling summary stream initialization failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Rolling summary stream initialization failed: {}", e.getMessage());
        }
    }
}
