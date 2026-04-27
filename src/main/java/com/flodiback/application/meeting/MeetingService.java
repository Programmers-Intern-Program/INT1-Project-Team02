package com.flodiback.application.meeting;

import org.springframework.stereotype.Service;

import com.flodiback.application.meeting.command.CreateMeetingCommand;
import com.flodiback.application.meeting.result.MeetingResult;
import com.flodiback.domain.meeting.repository.MeetingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;

    public MeetingResult create(CreateMeetingCommand command) {
        throw new UnsupportedOperationException("TODO");
    }

    public MeetingResult end(Long id) {
        throw new UnsupportedOperationException("TODO");
    }

    public MeetingResult getById(Long id) {
        throw new UnsupportedOperationException("TODO");
    }
}
