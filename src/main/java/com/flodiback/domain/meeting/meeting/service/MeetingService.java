package com.flodiback.domain.meeting.meeting.service;

import org.springframework.stereotype.Service;

import com.flodiback.domain.meeting.meeting.dto.CreateMeetingRequest;
import com.flodiback.domain.meeting.meeting.dto.CreateMeetingResponse;
import com.flodiback.domain.meeting.meeting.dto.MeetingDetailResponse;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;

    public CreateMeetingResponse create(CreateMeetingRequest req) {
        throw new UnsupportedOperationException("TODO");
    }

    public MeetingDetailResponse end(Long id) {
        throw new UnsupportedOperationException("TODO");
    }

    public MeetingDetailResponse getById(Long id) {
        throw new UnsupportedOperationException("TODO");
    }
}
