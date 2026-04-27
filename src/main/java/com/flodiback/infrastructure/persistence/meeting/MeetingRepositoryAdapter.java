package com.flodiback.infrastructure.persistence.meeting;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.flodiback.domain.meeting.entity.Meeting;
import com.flodiback.domain.meeting.repository.MeetingRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class MeetingRepositoryAdapter implements MeetingRepository {

    private final JpaMeetingRepository jpaMeetingRepository;

    @Override
    public Meeting save(Meeting meeting) {
        return jpaMeetingRepository.save(meeting);
    }

    @Override
    public Optional<Meeting> findById(Long id) {
        return jpaMeetingRepository.findById(id);
    }
}
