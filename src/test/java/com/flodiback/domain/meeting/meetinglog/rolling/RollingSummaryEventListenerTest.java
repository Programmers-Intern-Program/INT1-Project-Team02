package com.flodiback.domain.meeting.meetinglog.rolling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class RollingSummaryEventListenerTest {

    @Mock
    private RollingSummaryService rollingSummaryService;

    @Test
    void handle_compressesEventMeetingId() {
        RollingSummaryEventListener listener = new RollingSummaryEventListener(rollingSummaryService);

        listener.handle(new UtteranceSavedEvent(1L, 100L));

        verify(rollingSummaryService).compressIfNeeded(1L);
    }

    @Test
    void handle_runsAfterCommitWithRollingSummaryExecutor() throws NoSuchMethodException {
        Method handle = RollingSummaryEventListener.class.getMethod("handle", UtteranceSavedEvent.class);

        TransactionalEventListener eventListener = handle.getAnnotation(TransactionalEventListener.class);
        Async async = handle.getAnnotation(Async.class);

        assertThat(eventListener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(async.value()).isEqualTo("rollingSummaryExecutor");
    }
}
