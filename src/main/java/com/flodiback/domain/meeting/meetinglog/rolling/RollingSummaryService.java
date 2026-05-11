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

    static final int TOKEN_THRESHOLD = 1800;
    static final int KEEP_TURNS = 12;

    private static final String SYSTEM_PROMPT = """
            당신은 회의 내용을 압축하는 AI 요약기입니다.
            이전 rolling summary와 새 발화를 합쳐 현재 회의 상태를 갱신하세요.
            이전 rolling summary에 이미 있는 결정사항, 미결 사항, 액션 아이템은 보존하고,
            새 발화에서 나온 항목을 병합하세요.
            새 발화에서 기존 항목이 취소되거나 변경되면 최신 발화를 우선하세요.
            반드시 아래 섹션을 유지하고, 내용이 없는 섹션은 "- 없음"으로 채우세요.

            [흐름 요약]
            ...

            [현재 회의 결정사항]
            - ...

            [미결 사항]
            - ...

            [액션 아이템]
            - ...
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
