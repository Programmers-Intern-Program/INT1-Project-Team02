package com.flodiback.api.speech;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.speech.dto.InternalSpeechRequest;
import com.flodiback.domain.speech.dto.InternalSpeechResponse;
import com.flodiback.domain.speech.service.InternalSpeechService;
import com.flodiback.global.rsData.RsData;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class InternalSpeechController {

    private final InternalSpeechService internalSpeechService;

    @PostMapping("/speech")
    public ResponseEntity<RsData<InternalSpeechResponse>> receiveSpeech(
            @Valid @RequestBody InternalSpeechRequest request) {
        // Python 봇에서 전달한 STT 결과를 저장 처리로 넘긴다.
        InternalSpeechResponse response = internalSpeechService.saveSpeech(request);

        return ResponseEntity.ok(RsData.of("200-1", "발화가 저장되었습니다.", response));
    }
}
