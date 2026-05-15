package com.flodiback.global.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.project.worklog.event.WorkLogChangedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WorkLogWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkLogChanged(WorkLogChangedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/projects/" + event.projectId() + "/work-logs",
                new WorkLogChangedMessage("worklog.changed", event.projectId(), event.meetingId()));
    }

    record WorkLogChangedMessage(String type, Long projectId, Long meetingId) {}
}
