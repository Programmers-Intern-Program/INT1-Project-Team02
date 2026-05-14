package com.flodiback.global.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryProgress;
import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryService;
import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryUpdatedEvent;
import com.flodiback.domain.meeting.meetinglog.rolling.UtteranceSavedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RollingSummaryProgressWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final RollingSummaryService rollingSummaryService;

    @Async("contextProgressExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUtteranceSaved(UtteranceSavedEvent event) {
        publish(rollingSummaryService.getProgress(event.meetingId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRollingSummaryUpdated(RollingSummaryUpdatedEvent event) {
        publish(rollingSummaryService.getProgress(event.meetingId()));
    }

    private void publish(RollingSummaryProgress progress) {
        messagingTemplate.convertAndSend("/topic/meetings/" + progress.meetingId() + "/context-progress", progress);
    }
}
