package com.flodiback.domain.meeting.meetinglog.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;

public interface UtteranceRepository extends JpaRepository<Utterance, Long> {

    List<Utterance> findTop20ByMeetingIdOrderBySpokenAtDesc(Long meetingId);

    List<Utterance> findByMeetingOrderBySpokenAtAsc(Meeting meeting);

    List<Utterance> findByMeetingAndSequenceNoGreaterThanOrderBySequenceNoAsc(Meeting meeting, Long sequenceNo);

    List<Utterance> findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(Meeting meeting, LocalDateTime safeUntil);

    List<Utterance> findByMeetingAndCreatedAtGreaterThanAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
            Meeting meeting, LocalDateTime after, LocalDateTime safeUntil);

    List<Utterance> findByMeetingAndSequenceNoGreaterThanAndCreatedAtLessThanEqualOrderBySequenceNoAsc(
            Meeting meeting, Long sequenceNo, LocalDateTime safeUntil);

    List<Utterance> findByMeetingAndCreatedAtGreaterThanOrderBySpokenAtAscSequenceNoAscIdAsc(
            Meeting meeting, LocalDateTime after);

    long countByMeeting(Meeting meeting);
}
