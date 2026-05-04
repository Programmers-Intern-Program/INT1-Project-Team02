package com.flodiback.domain.meeting.meetinglog.rolling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

@ExtendWith(MockitoExtension.class)
class RollingSummaryConsumerTest {

    @Mock
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @Mock
    private RollingSummaryService rollingSummaryService;

    @Test
    void onMessage_counter가_3000미만이면_writer를_호출하지_않고_ack() {
        given(redisTemplate.opsForStream()).willReturn(streamOperations);
        RollingSummaryConsumer consumer =
                new RollingSummaryConsumer(listenerContainer, redisTemplate, rollingSummaryService);
        MapRecord<String, String, String> message = message(Map.of("meetingId", "1", "tokenCount", "2999"));

        consumer.onMessage(message);

        verify(rollingSummaryService, never()).compressIfNeeded(any());
        verify(streamOperations)
                .acknowledge(
                        RollingSummaryStreamConstants.STREAM_KEY,
                        RollingSummaryStreamConstants.GROUP_NAME,
                        message.getId());
    }

    @Test
    void onMessage_counter가_3000이상이면_writer호출후_DB기준_counter로_재설정하고_ack() {
        given(redisTemplate.opsForStream()).willReturn(streamOperations);
        given(rollingSummaryService.calculateUncompressedTokenCount(1L)).willReturn(500L);
        RollingSummaryConsumer consumer =
                new RollingSummaryConsumer(listenerContainer, redisTemplate, rollingSummaryService);
        MapRecord<String, String, String> message = message(Map.of("meetingId", "1", "tokenCount", "3000"));

        consumer.onMessage(message);

        verify(rollingSummaryService).compressIfNeeded(1L);
        verify(rollingSummaryService).calculateUncompressedTokenCount(1L);
        verify(streamOperations)
                .acknowledge(
                        RollingSummaryStreamConstants.STREAM_KEY,
                        RollingSummaryStreamConstants.GROUP_NAME,
                        message.getId());
    }

    @Test
    void onMessage_tokenCount파싱실패는_0으로_처리하고_ack() {
        given(redisTemplate.opsForStream()).willReturn(streamOperations);
        RollingSummaryConsumer consumer =
                new RollingSummaryConsumer(listenerContainer, redisTemplate, rollingSummaryService);
        MapRecord<String, String, String> message = message(Map.of("meetingId", "1", "tokenCount", "not-a-number"));

        consumer.onMessage(message);

        verify(rollingSummaryService, never()).compressIfNeeded(any());
        verify(streamOperations)
                .acknowledge(
                        RollingSummaryStreamConstants.STREAM_KEY,
                        RollingSummaryStreamConstants.GROUP_NAME,
                        message.getId());
    }

    @Test
    void onMessage_meetingId가_없으면_writer를_호출하지_않고_ack() {
        given(redisTemplate.opsForStream()).willReturn(streamOperations);
        RollingSummaryConsumer consumer =
                new RollingSummaryConsumer(listenerContainer, redisTemplate, rollingSummaryService);
        MapRecord<String, String, String> message = message(Map.of("tokenCount", "100"));

        consumer.onMessage(message);

        verify(rollingSummaryService, never()).compressIfNeeded(any());
        verify(streamOperations)
                .acknowledge(
                        RollingSummaryStreamConstants.STREAM_KEY,
                        RollingSummaryStreamConstants.GROUP_NAME,
                        message.getId());
    }

    private MapRecord<String, String, String> message(Map<String, String> fields) {
        return MapRecord.create(RollingSummaryStreamConstants.STREAM_KEY, fields)
                .withId(RecordId.of("1-0"));
    }
}
