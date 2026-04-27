package com.flodiback.domain.meeting.repository;

import java.util.Optional;

import com.flodiback.domain.meeting.entity.Meeting;

public interface MeetingRepository {

    Meeting save(Meeting meeting);

    Optional<Meeting> findById(Long id);
}
