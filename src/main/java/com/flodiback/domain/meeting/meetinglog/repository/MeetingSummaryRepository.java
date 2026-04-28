package com.flodiback.domain.meeting.meetinglog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;

public interface MeetingSummaryRepository extends JpaRepository<MeetingSummary, Long> {

    @Query(
            "SELECT ms FROM MeetingSummary ms WHERE ms.meeting.project.id = :projectId AND ms.meeting.id <> :currentMeetingId")
    List<MeetingSummary> findPastByProjectId(
            @Param("projectId") Long projectId, @Param("currentMeetingId") Long currentMeetingId);
}
