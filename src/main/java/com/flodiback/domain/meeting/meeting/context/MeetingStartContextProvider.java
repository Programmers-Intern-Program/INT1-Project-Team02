package com.flodiback.domain.meeting.meeting.context;

public interface MeetingStartContextProvider {

    MeetingStartContext getOrCreate(Long meetingId);

    void invalidate(Long meetingId);
}
