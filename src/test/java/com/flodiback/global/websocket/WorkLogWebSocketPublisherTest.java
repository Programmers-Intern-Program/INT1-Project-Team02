package com.flodiback.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.project.worklog.event.WorkLogChangedEvent;

@ExtendWith(MockitoExtension.class)
class WorkLogWebSocketPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void onWorkLogChanged_publishesProjectWorkLogChange() {
        WorkLogWebSocketPublisher publisher = new WorkLogWebSocketPublisher(messagingTemplate);

        publisher.onWorkLogChanged(new WorkLogChangedEvent(1L, 10L));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate)
                .convertAndSend(
                        org.mockito.ArgumentMatchers.eq("/topic/projects/1/work-logs"), payloadCaptor.capture());
        Object payload = payloadCaptor.getValue();
        assertThat(payload).hasFieldOrPropertyWithValue("type", "worklog.changed");
        assertThat(payload).hasFieldOrPropertyWithValue("projectId", 1L);
        assertThat(payload).hasFieldOrPropertyWithValue("meetingId", 10L);
    }

    @Test
    void onWorkLogChanged_runsAfterCommit() throws NoSuchMethodException {
        Method method = WorkLogWebSocketPublisher.class.getMethod("onWorkLogChanged", WorkLogChangedEvent.class);

        TransactionalEventListener eventListener =
                AnnotatedElementUtils.findMergedAnnotation(method, TransactionalEventListener.class);

        assertThat(eventListener).isNotNull();
        assertThat(eventListener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
