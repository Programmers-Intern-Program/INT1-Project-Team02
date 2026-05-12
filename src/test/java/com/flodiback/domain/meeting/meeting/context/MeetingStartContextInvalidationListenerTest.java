package com.flodiback.domain.meeting.meeting.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flodiback.domain.meeting.meeting.event.MeetingEndedEvent;

class MeetingStartContextInvalidationListenerTest {

    @Test
    void handle_invalidatesMeetingStartContext() {
        MeetingStartContextProvider provider = org.mockito.Mockito.mock(MeetingStartContextProvider.class);
        MeetingStartContextInvalidationListener listener = new MeetingStartContextInvalidationListener(provider);

        listener.handle(new MeetingEndedEvent(1L));

        verify(provider).invalidate(1L);
    }

    @Test
    void handle_usesAfterCommitTransactionalEventListener() throws Exception {
        Method handle = MeetingStartContextInvalidationListener.class.getMethod("handle", MeetingEndedEvent.class);

        TransactionalEventListener annotation = handle.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
