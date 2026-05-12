package com.flodiback.domain.meeting.meetinglog.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;

public interface UtteranceRepository extends JpaRepository<Utterance, Long> {

    List<Utterance> findTop20ByMeetingIdOrderBySpeechStartedAtDesc(Long meetingId);

    List<Utterance> findByMeetingOrderByIdAsc(Meeting meeting);

    List<Utterance> findByMeetingAndIdGreaterThanOrderByIdAsc(Meeting meeting, Long id);

    List<Utterance> findByMeetingOrderByIdDesc(Meeting meeting, Pageable pageable);

    List<Utterance> findByMeetingAndIdGreaterThanOrderByIdDesc(Meeting meeting, Long id, Pageable pageable);

    @Query("""
            select u.speakerDiscordId as speakerDiscordId,
                   u.speakerName as speakerName,
                   u.id as firstUtteranceId
            from Utterance u
            where u.meeting = :meeting
              and u.speakerDiscordId is not null
              and trim(u.speakerDiscordId) <> ''
              and u.id = (
                  select min(u2.id)
                  from Utterance u2
                  where u2.meeting = :meeting
                    and u2.speakerDiscordId = u.speakerDiscordId
              )
            order by u.id asc
            """)
    List<SpeakerProjection> findDistinctSpeakersByMeeting(@Param("meeting") Meeting meeting);

    interface SpeakerProjection {
        String getSpeakerDiscordId();

        String getSpeakerName();

        Long getFirstUtteranceId();
    }
}
