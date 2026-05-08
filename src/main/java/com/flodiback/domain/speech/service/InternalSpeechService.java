package com.flodiback.domain.speech.service;

import org.springframework.stereotype.Service;

import com.flodiback.domain.speech.dto.InternalSpeechRequest;
import com.flodiback.domain.speech.dto.InternalSpeechResponse;
import com.flodiback.domain.speech.dto.SavedSpeechResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InternalSpeechService {

    private final SpeechPersistenceService speechPersistenceService;
    private final SpeechAiAnswerService speechAiAnswerService;

    public InternalSpeechResponse saveSpeech(InternalSpeechRequest request) {
        SavedSpeechResult savedSpeech = speechPersistenceService.saveUtterance(request);

        // DB 저장 트랜잭션이 끝난 뒤 GLM을 호출해 DB 커넥션을 오래 잡지 않게 합니다.
        String aiAnswer = speechAiAnswerService.generateAnswerIfCalled(savedSpeech.meetingId(), request.text());

        return new InternalSpeechResponse(savedSpeech.utteranceId(), savedSpeech.meetingId(), aiAnswer);
    }
}
