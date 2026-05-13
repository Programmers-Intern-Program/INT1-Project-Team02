package com.flodiback.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryProgress;
import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryService;
import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryUpdatedEvent;
import com.flodiback.domain.meeting.meetinglog.rolling.UtteranceSavedEvent;

@ExtendWith(MockitoExtension.class)
class RollingSummaryProgressWebSocketPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RollingSummaryService rollingSummaryService;

    @Test
    void onUtteranceSaved_publishesContextProgress() {
        RollingSummaryProgressWebSocketPublisher publisher =
                new RollingSummaryProgressWebSocketPublisher(messagingTemplate, rollingSummaryService);
        RollingSummaryProgress progress = new RollingSummaryProgress(1L, 1100L, 2200, 1100L, 50, 50, false);
        given(rollingSummaryService.getProgress(1L)).willReturn(progress);

        publisher.onUtteranceSaved(new UtteranceSavedEvent(1L, 100L));

        verify(messagingTemplate).convertAndSend("/topic/meetings/1/context-progress", progress);
    }

    @Test
    void onRollingSummaryUpdated_publishesContextProgress() {
        RollingSummaryProgressWebSocketPublisher publisher =
                new RollingSummaryProgressWebSocketPublisher(messagingTemplate, rollingSummaryService);
        RollingSummaryProgress progress = new RollingSummaryProgress(1L, 0L, 2200, 2200L, 0, 100, false);
        given(rollingSummaryService.getProgress(1L)).willReturn(progress);

        publisher.onRollingSummaryUpdated(new RollingSummaryUpdatedEvent(1L, "summary", 2));

        verify(messagingTemplate).convertAndSend("/topic/meetings/1/context-progress", progress);
    }

    @Test
    void onUtteranceSaved_runsAfterCommitWithContextProgressExecutor() throws NoSuchMethodException {
        Method method =
                RollingSummaryProgressWebSocketPublisher.class.getMethod("onUtteranceSaved", UtteranceSavedEvent.class);

        Async async = AnnotatedElementUtils.findMergedAnnotation(method, Async.class);
        TransactionalEventListener eventListener =
                AnnotatedElementUtils.findMergedAnnotation(method, TransactionalEventListener.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("contextProgressExecutor");
        assertThat(eventListener).isNotNull();
        assertThat(eventListener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void onRollingSummaryUpdated_runsAfterCommitWithoutAsync() throws NoSuchMethodException {
        Method method = RollingSummaryProgressWebSocketPublisher.class.getMethod(
                "onRollingSummaryUpdated", RollingSummaryUpdatedEvent.class);

        Async async = AnnotatedElementUtils.findMergedAnnotation(method, Async.class);
        TransactionalEventListener eventListener =
                AnnotatedElementUtils.findMergedAnnotation(method, TransactionalEventListener.class);

        assertThat(async).isNull();
        assertThat(eventListener).isNotNull();
        assertThat(eventListener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
