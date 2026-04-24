package com.flodiback.domain.meeting.meeting.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.meeting.meeting.entity.Meeting;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {}
