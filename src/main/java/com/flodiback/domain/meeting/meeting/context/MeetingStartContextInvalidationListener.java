package com.flodiback.domain.meeting.meeting.context;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.meeting.meeting.event.MeetingEndedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MeetingStartContextInvalidationListener {

    private final MeetingStartContextProvider meetingStartContextProvider;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MeetingEndedEvent event) {
        meetingStartContextProvider.invalidate(event.meetingId());
    }
}
