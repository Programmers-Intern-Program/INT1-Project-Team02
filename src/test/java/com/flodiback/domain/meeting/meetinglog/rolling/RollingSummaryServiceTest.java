package com.flodiback.domain.meeting.meetinglog.rolling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.ai.service.AiChatService;

@ExtendWith(MockitoExtension.class)
class RollingSummaryServiceTest {

    @Mock
    private RollingSummaryPersistenceService rollingSummaryPersistenceService;

    @Mock
    private AiChatService aiChatService;

    @Test
    void compressIfNeeded_doesNotOwnTransactionBoundary() throws NoSuchMethodException {
        Method compressIfNeeded = RollingSummaryService.class.getMethod("compressIfNeeded", Long.class);

        assertThat(AnnotatedElementUtils.hasAnnotation(compressIfNeeded, Transactional.class))
                .isFalse();
    }

    @Test
    void systemPrompt_containsStructuredSectionsAndMergeInstruction() throws Exception {
        Field promptField = RollingSummaryService.class.getDeclaredField("SYSTEM_PROMPT");
        promptField.setAccessible(true);

        String prompt = (String) promptField.get(null);

        assertThat(prompt)
                .contains("[흐름 요약]")
                .contains("[현재 회의 결정사항]")
                .contains("[미결 사항]")
                .contains("[액션 아이템]")
                .contains("보존")
                .contains("병합")
                .contains("최신 발화");
    }

    @Test
    void compressIfNeeded_skipsWhenCandidateIsEmpty() {
        RollingSummaryService service = new RollingSummaryService(rollingSummaryPersistenceService, aiChatService);
        given(rollingSummaryPersistenceService.prepareCompression(1L)).willReturn(Optional.empty());

        service.compressIfNeeded(1L);

        verify(aiChatService, never())
                .generateAnswer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void compressIfNeeded_skipsSaveWhenGlmReturnsBlank() {
        RollingSummaryService service = new RollingSummaryService(rollingSummaryPersistenceService, aiChatService);
        RollingSummaryPersistenceService.CompressionCandidate candidate = candidate();
        given(rollingSummaryPersistenceService.prepareCompression(1L)).willReturn(Optional.of(candidate));
        given(aiChatService.generateAnswer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(" ");

        service.compressIfNeeded(1L);

        verify(rollingSummaryPersistenceService, never())
                .saveCompression(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void compressIfNeeded_savesGeneratedSummary() {
        RollingSummaryService service = new RollingSummaryService(rollingSummaryPersistenceService, aiChatService);
        RollingSummaryPersistenceService.CompressionCandidate candidate = candidate();
        given(rollingSummaryPersistenceService.prepareCompression(1L)).willReturn(Optional.of(candidate));
        given(aiChatService.generateAnswer(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("prompt")))
                .willReturn("summary");

        service.compressIfNeeded(1L);

        verify(rollingSummaryPersistenceService).saveCompression(candidate, "summary");
    }

    @Test
    void compressIfNeeded_swallowsGlmException() {
        RollingSummaryService service = new RollingSummaryService(rollingSummaryPersistenceService, aiChatService);
        RollingSummaryPersistenceService.CompressionCandidate candidate = candidate();
        given(rollingSummaryPersistenceService.prepareCompression(1L)).willReturn(Optional.of(candidate));
        given(aiChatService.generateAnswer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willThrow(new RuntimeException("glm failed"));

        assertThatCode(() -> service.compressIfNeeded(1L)).doesNotThrowAnyException();
    }

    private RollingSummaryPersistenceService.CompressionCandidate candidate() {
        return new RollingSummaryPersistenceService.CompressionCandidate(1L, null, null, 11L, "prompt");
    }
}
