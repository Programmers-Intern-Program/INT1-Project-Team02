package com.flodiback.api.speech;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.speech.dto.CaptionEvent;
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
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/speech")
    public RsData<InternalSpeechResponse> receiveSpeech(@Valid @RequestBody InternalSpeechRequest request) {
        InternalSpeechResponse response = internalSpeechService.saveSpeech(request);

        CaptionEvent event = CaptionEvent.finalEvent(
                request.meetingId(), request.speakerDiscordId(), request.speakerName(), request.text());
        messagingTemplate.convertAndSend("/topic/meetings/" + request.meetingId() + "/captions", event);

        return RsData.of("200-1", "발화가 저장되었습니다.", response);
    }
}
