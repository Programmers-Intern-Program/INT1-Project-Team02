package com.flodiback.global.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.meeting.meeting.event.MeetingEndedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MeetingStatusWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingEnded(MeetingEndedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/meetings/" + event.meetingId() + "/status",
                new MeetingStatusMessage("meeting.ended", event.meetingId()));
    }

    record MeetingStatusMessage(String type, Long meetingId) {}
}
