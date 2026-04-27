package com.flodiback.domain.meeting.meetinglog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.meeting.meetinglog.entity.Utterance;

public interface UtteranceRepository extends JpaRepository<Utterance, Long> {}
