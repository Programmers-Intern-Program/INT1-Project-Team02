package com.flodiback.domain.meeting.meetinglog.rolling;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RollingSummaryEventListener {

    private final RollingSummaryService rollingSummaryService;

    @Async("rollingSummaryExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UtteranceSavedEvent event) {
        rollingSummaryService.compressIfNeeded(event.meetingId());
    }
}
