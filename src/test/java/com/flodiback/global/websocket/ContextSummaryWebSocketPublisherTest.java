package com.flodiback.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryUpdatedEvent;

@ExtendWith(MockitoExtension.class)
class ContextSummaryWebSocketPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void onRollingSummaryUpdated_publishesContextSummary() {
        ContextSummaryWebSocketPublisher publisher = new ContextSummaryWebSocketPublisher(messagingTemplate);

        publisher.onRollingSummaryUpdated(new RollingSummaryUpdatedEvent(1L, "summary", 2));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate)
                .convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/meetings/1/context"), payloadCaptor.capture());
        Object payload = payloadCaptor.getValue();
        assertThat(payload).hasFieldOrPropertyWithValue("meetingId", 1L);
        assertThat(payload).hasFieldOrPropertyWithValue("summary", "summary");
        assertThat(payload).hasFieldOrPropertyWithValue("version", 2);
    }
}
