package com.flodiback.domain.meeting.meeting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.global.enums.MeetingStatus;

import jakarta.persistence.LockModeType;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByStatus(MeetingStatus status);

    @Query("select m.id from Meeting m where m.status = :status")
    List<Long> findIdsByStatus(@Param("status") MeetingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") Long id);
}
