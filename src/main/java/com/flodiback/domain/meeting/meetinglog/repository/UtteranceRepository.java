package com.flodiback.domain.meeting.meetinglog.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;

public interface UtteranceRepository extends JpaRepository<Utterance, Long> {

    List<Utterance> findTop20ByMeetingIdOrderBySpeechStartedAtDesc(Long meetingId);

    List<Utterance> findByMeetingOrderBySpeechStartedAtAsc(Meeting meeting);

    List<Utterance> findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(Meeting meeting, LocalDateTime safeUntil);

    List<Utterance> findByMeetingAndCreatedAtGreaterThanAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
            Meeting meeting, LocalDateTime after, LocalDateTime safeUntil);

    List<Utterance> findByMeetingAndCreatedAtGreaterThanOrderBySpeechStartedAtAscIdAsc(
            Meeting meeting, LocalDateTime after);
}
