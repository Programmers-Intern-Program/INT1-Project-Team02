package com.flodiback.domain.meeting.meetinglog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;

public interface UtteranceRepository extends JpaRepository<Utterance, Long> {

    List<Utterance> findTop20ByMeetingIdOrderBySpokenAtDesc(Long meetingId);

    List<Utterance> findByMeetingOrderBySpokenAtAsc(Meeting meeting);

    long countByMeeting(Meeting meeting);
}
