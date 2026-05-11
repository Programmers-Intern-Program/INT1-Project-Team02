package com.flodiback.global.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryUpdatedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ContextSummaryWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRollingSummaryUpdated(RollingSummaryUpdatedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/meetings/" + event.meetingId() + "/context",
                new ContextSummaryMessage(event.meetingId(), event.summary(), event.version()));
    }

    record ContextSummaryMessage(Long meetingId, String summary, Integer version) {}
}
