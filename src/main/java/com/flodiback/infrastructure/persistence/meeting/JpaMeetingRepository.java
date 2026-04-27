package com.flodiback.infrastructure.persistence.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.meeting.entity.Meeting;

interface JpaMeetingRepository extends JpaRepository<Meeting, Long> {}
