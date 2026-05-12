package com.flodiback.domain.meeting.meeting.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;

public interface ContextCacheRepository extends JpaRepository<ContextCache, Long> {

    Optional<ContextCache> findTopByMeetingOrderByVersionDesc(Meeting meeting);
}
