package com.flodiback.domain.speech.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.meeting.meetinglog.rolling.UtteranceSavedEvent;
import com.flodiback.domain.speech.dto.InternalSpeechRequest;
import com.flodiback.domain.speech.dto.SavedSpeechResult;
import com.flodiback.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpeechPersistenceService {

    private final MeetingRepository meetingRepository;
    private final UtteranceRepository utteranceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SavedSpeechResult saveUtterance(InternalSpeechRequest request) {
        // 이 메서드는 발화 저장만 짧게 처리하는 트랜잭션 경계입니다.
        Meeting meeting = meetingRepository
                .findById(request.meetingId())
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));

        // 현재 방식은 동시 요청에서 같은 sequenceNo가 생길 수 있어, 별도 이슈에서 보강합니다.
        long sequenceNo = utteranceRepository.countByMeeting(meeting) + 1;

        int tokenCount = estimateTokenCount(request.text());
        Utterance utterance = Utterance.builder()
                .meeting(meeting)
                .speakerDiscordId(request.speakerDiscordId())
                .speakerName(request.speakerName())
                .content(request.text())
                .spokenAt(request.timestamp())
                .sequenceNo(sequenceNo)
                .tokenCount(tokenCount)
                .build();

        Utterance savedUtterance = utteranceRepository.save(utterance);

        // 저장 커밋 이후 rolling summary가 처리할 수 있도록 발화 저장 이벤트를 발행합니다.
        eventPublisher.publishEvent(
                new UtteranceSavedEvent(meeting.getId(), savedUtterance.getId(), sequenceNo, tokenCount));

        return new SavedSpeechResult(savedUtterance.getId(), meeting.getId());
    }

    private int estimateTokenCount(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
