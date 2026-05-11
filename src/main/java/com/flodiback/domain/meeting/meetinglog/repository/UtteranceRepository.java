package com.flodiback.domain.meeting.meetinglog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;

public interface UtteranceRepository extends JpaRepository<Utterance, Long> {

    List<Utterance> findTop20ByMeetingIdOrderBySpeechStartedAtDesc(Long meetingId);

    List<Utterance> findByMeetingOrderByIdAsc(Meeting meeting);

    List<Utterance> findByMeetingAndIdGreaterThanOrderByIdAsc(Meeting meeting, Long id);

    List<Utterance> findTop30ByMeetingOrderByIdDesc(Meeting meeting);

    List<Utterance> findTop30ByMeetingAndIdGreaterThanOrderByIdDesc(Meeting meeting, Long id);
}
