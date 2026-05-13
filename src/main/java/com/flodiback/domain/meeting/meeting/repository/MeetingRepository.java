package com.flodiback.domain.meeting.meeting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.global.enums.MeetingStatus;

import jakarta.persistence.LockModeType;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    Optional<Meeting> findFirstByProjectAndStatusOrderByStartedAtDesc(Project project, MeetingStatus status);

    List<Meeting> findByStatus(MeetingStatus status);

    List<Meeting> findByProjectId(Long projectId);

    @Query("select m.id from Meeting m where m.status = :status")
    List<Long> findIdsByStatus(@Param("status") MeetingStatus status);

    @Query("select m from Meeting m where m.project.id in :projectIds and m.status = :status order by m.startedAt desc")
    List<Meeting> findActiveByProjectIds(
            @Param("projectIds") List<Long> projectIds, @Param("status") MeetingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") Long id);
}
