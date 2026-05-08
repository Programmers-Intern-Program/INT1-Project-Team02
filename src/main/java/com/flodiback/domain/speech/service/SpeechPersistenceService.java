package com.flodiback.domain.speech.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.meeting.meetinglog.rolling.UtteranceSavedEvent;
import com.flodiback.domain.speech.dto.InternalSpeechRequest;
import com.flodiback.domain.speech.dto.SavedSpeechResult;
import com.flodiback.global.exception.ServiceException;
import com.flodiback.global.util.TokenEstimator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpeechPersistenceService {

    private final MeetingRepository meetingRepository;
    private final UtteranceRepository utteranceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SavedSpeechResult saveUtterance(InternalSpeechRequest request) {
        Meeting meeting = meetingRepository
                .findById(request.meetingId())
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));

        Utterance utterance = Utterance.builder()
                .meeting(meeting)
                .speakerDiscordId(request.speakerDiscordId())
                .speakerName(request.speakerName())
                .content(request.text())
                .speechStartedAt(request.speechStartedAt())
                .speechEndedAt(request.speechEndedAt())
                .tokenCount(TokenEstimator.estimate(request.text()))
                .build();

        Utterance savedUtterance = utteranceRepository.save(utterance);
        eventPublisher.publishEvent(new UtteranceSavedEvent(meeting.getId(), savedUtterance.getId()));
        return new SavedSpeechResult(savedUtterance.getId(), meeting.getId());
    }
}
