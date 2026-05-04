package com.flodiback.domain.meeting.meetinglog.rolling;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollingSummaryEventPublisher {

    private final RedisTemplate<String, String> redisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(UtteranceSavedEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("meetingId", String.valueOf(event.meetingId()));
        fields.put("utteranceId", String.valueOf(event.utteranceId()));
        fields.put("sequenceNo", String.valueOf(event.sequenceNo()));
        fields.put("tokenCount", String.valueOf(event.tokenCount()));

        try {
            redisTemplate
                    .opsForStream()
                    .add(StreamRecords.newRecord()
                            .ofMap(fields)
                            .withStreamKey(RollingSummaryStreamConstants.STREAM_KEY));
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to publish rolling summary event. meetingId={}, utteranceId={}, sequenceNo={}, tokenCount={}, reason={}",
                    event.meetingId(),
                    event.utteranceId(),
                    event.sequenceNo(),
                    event.tokenCount(),
                    e.getMessage());
        }
    }
}
