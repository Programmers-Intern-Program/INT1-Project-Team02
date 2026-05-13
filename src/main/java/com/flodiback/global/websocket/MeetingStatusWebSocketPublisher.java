package com.flodiback.global.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.meeting.meeting.event.MeetingEndedEvent;
import com.flodiback.domain.meeting.meeting.event.MeetingStartedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MeetingStatusWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingStarted(MeetingStartedEvent event) {
        if (event.projectId() == null) return;
        messagingTemplate.convertAndSend(
                "/topic/projects/" + event.projectId() + "/status",
                new ProjectStatusMessage("meeting.started", event.meetingId(), event.channelId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingEnded(MeetingEndedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/meetings/" + event.meetingId() + "/status",
                new MeetingStatusMessage("meeting.ended", event.meetingId()));
        if (event.projectId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/projects/" + event.projectId() + "/status",
                    new ProjectStatusMessage("meeting.ended", event.meetingId(), event.channelId()));
        }
    }

    record ProjectStatusMessage(String type, Long meetingId, String channelId) {}

    record MeetingStatusMessage(String type, Long meetingId) {}
}
