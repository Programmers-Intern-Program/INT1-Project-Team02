package com.flodiback.global.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.project.project.event.ProjectCreatedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProjectWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectCreated(ProjectCreatedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/projects/status",
                new ProjectCreatedMessage("project.created", event.projectId(), event.guildId()));
    }

    record ProjectCreatedMessage(String type, Long projectId, String guildId) {}
}
