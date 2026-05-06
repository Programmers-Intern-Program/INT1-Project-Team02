package com.flodiback.domain.meeting.meetinglog.rolling;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.flodiback.domain.ai.service.AiChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RollingSummaryService {

    static final int TOKEN_THRESHOLD = 3000;
    static final int KEEP_TURNS = 20;

    private static final String SYSTEM_PROMPT = """
            당신은 회의 내용을 압축하는 AI 요약기입니다.
            이전 요약이 있으면 유지해야 할 결정, 미결사항, 작업 맥락을 보존하고,
            새 발화에서 추가된 핵심만 반영해 현재 회의 rolling summary를 한국어로 갱신하세요.
            """;

    private final RollingSummaryPersistenceService rollingSummaryPersistenceService;
    private final AiChatService aiChatService;

    public void compressIfNeeded(Long meetingId) {
        try {
            rollingSummaryPersistenceService.prepareCompression(meetingId).ifPresent(candidate -> {
                String compressedText = aiChatService.generateAnswer(SYSTEM_PROMPT, candidate.userPrompt());
                if (!StringUtils.hasText(compressedText)) {
                    log.warn("Rolling summary GLM returned blank result. meetingId={}", meetingId);
                    return;
                }
                rollingSummaryPersistenceService.saveCompression(candidate, compressedText);
            });
        } catch (RuntimeException e) {
            log.warn("Rolling summary compression failed. meetingId={}, reason={}", meetingId, e.getMessage());
        }
    }

    public long calculateUncompressedTokenCount(Long meetingId) {
        return rollingSummaryPersistenceService.calculateUncompressedTokenCount(meetingId);
    }
}
