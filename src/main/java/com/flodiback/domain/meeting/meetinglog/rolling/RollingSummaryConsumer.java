package com.flodiback.domain.meeting.meetinglog.rolling;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollingSummaryConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private final RedisTemplate<String, String> redisTemplate;
    private final RollingSummaryService rollingSummaryService;
    private final Map<Long, Long> tokenCounters = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        try {
            recoverPendingBestEffort();
            listenerContainer.receive(
                    Consumer.from(
                            RollingSummaryStreamConstants.GROUP_NAME, RollingSummaryStreamConstants.CONSUMER_NAME),
                    StreamOffset.create(RollingSummaryStreamConstants.STREAM_KEY, ReadOffset.lastConsumed()),
                    this);
            listenerContainer.start();
        } catch (RuntimeException e) {
            log.warn("Rolling summary consumer failed to start: {}", e.getMessage());
        }
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Long meetingId = null;
        try {
            Map<String, String> values = message.getValue();
            meetingId = parseLong(values.get("meetingId"));
            if (meetingId == null) {
                return;
            }

            long tokenCount = parseTokenCount(values.get("tokenCount"));
            long current = tokenCounters.merge(meetingId, tokenCount, Long::sum);
            if (current >= RollingSummaryStreamConstants.TOKEN_THRESHOLD) {
                rollingSummaryService.compressIfNeeded(meetingId);
                tokenCounters.put(meetingId, rollingSummaryService.calculateUncompressedTokenCount(meetingId));
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Rolling summary consumer failed. messageId={}, meetingId={}, reason={}",
                    message.getId(),
                    meetingId,
                    e.getMessage());
        } finally {
            acknowledge(message);
        }
    }

    private void acknowledge(MapRecord<String, String, String> message) {
        try {
            redisTemplate
                    .opsForStream()
                    .acknowledge(
                            RollingSummaryStreamConstants.STREAM_KEY,
                            RollingSummaryStreamConstants.GROUP_NAME,
                            message.getId());
        } catch (RuntimeException e) {
            log.warn("Rolling summary XACK failed. messageId={}, reason={}", message.getId(), e.getMessage());
        }
    }

    private void recoverPendingBestEffort() {
        try {
            PendingMessages pending = redisTemplate
                    .opsForStream()
                    .pending(
                            RollingSummaryStreamConstants.STREAM_KEY,
                            Consumer.from(
                                    RollingSummaryStreamConstants.GROUP_NAME,
                                    RollingSummaryStreamConstants.CONSUMER_NAME),
                            Range.unbounded(),
                            10);
            if (!pending.isEmpty()) {
                log.info("Rolling summary pending messages exist. count={}", pending.size());
            }
        } catch (RuntimeException e) {
            log.warn("Rolling summary pending recovery skipped: {}", e.getMessage());
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long parseTokenCount(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
