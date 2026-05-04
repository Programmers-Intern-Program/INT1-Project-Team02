package com.flodiback.domain.meeting.meetinglog.rolling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flodiback.domain.ai.service.AiChatService;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;

@ExtendWith(MockitoExtension.class)
class RollingSummaryServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-04T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime SAFE_UNTIL = LocalDateTime.of(2026, 5, 4, 11, 59, 50);
    private static final LocalDateTime OLD_CREATED_AT = LocalDateTime.of(2026, 5, 4, 11, 59, 0);

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private UtteranceRepository utteranceRepository;

    @Mock
    private ContextCacheRepository contextCacheRepository;

    @Mock
    private AiChatService aiChatService;

    private RollingSummaryService rollingSummaryService;

    @BeforeEach
    void setUp() {
        rollingSummaryService = new RollingSummaryService(
                meetingRepository, utteranceRepository, contextCacheRepository, aiChatService, FIXED_CLOCK);
    }

    @Test
    void compressIfNeeded_skipsWhenEligibleTokensBelowThreshold() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(List.of(utterance(meeting, 1L, 100), utterance(meeting, 2L, 200)));

        rollingSummaryService.compressIfNeeded(1L);

        verify(contextCacheRepository, never()).save(any());
        verify(aiChatService, never()).generateAnswer(any(), any());
    }

    @Test
    void compressIfNeeded_compressesEligibleUtterancesExceptLatestTail() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        List<Utterance> utterances = utterances(meeting, 1, 31, OLD_CREATED_AT);
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(utterances);
        given(aiChatService.generateAnswer(any(), any())).willReturn("summary");

        rollingSummaryService.compressIfNeeded(1L);

        ArgumentCaptor<ContextCache> cacheCaptor = ArgumentCaptor.forClass(ContextCache.class);
        verify(contextCacheRepository).save(cacheCaptor.capture());
        ContextCache saved = cacheCaptor.getValue();
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getStartSequenceNo()).isEqualTo(1L);
        assertThat(saved.getEndSequenceNo()).isEqualTo(11L);
        assertThat(saved.getCompressedText()).isEqualTo("summary");
        assertThat(saved.getCompressedUntilCreatedAt()).isEqualTo(OLD_CREATED_AT.plusSeconds(11));
    }

    @Test
    void compressIfNeeded_treatsNullTokenCountAsZero() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(List.of(utterance(meeting, 1L, null), utterance(meeting, 2L, 2999)));

        rollingSummaryService.compressIfNeeded(1L);

        verify(contextCacheRepository, never()).save(any());
    }

    @Test
    void compressIfNeeded_usesPreviousSummaryAndCreatedAtWatermark() {
        Meeting meeting = meeting();
        ContextCache latest = ContextCache.builder()
                .meeting(meeting)
                .version(3)
                .compressedText("previous summary")
                .startSequenceNo(1L)
                .endSequenceNo(5L)
                .tokenCount(30)
                .compressedUntilCreatedAt(OLD_CREATED_AT)
                .build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(latest));
        List<Utterance> utterances = utterances(meeting, 6, 36, OLD_CREATED_AT.plusSeconds(1));
        given(utteranceRepository.findByMeetingAndCreatedAtGreaterThanAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                        meeting, OLD_CREATED_AT, SAFE_UNTIL))
                .willReturn(utterances);
        given(aiChatService.generateAnswer(any(), any())).willReturn("new summary");

        rollingSummaryService.compressIfNeeded(1L);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateAnswer(any(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("previous summary");
        ArgumentCaptor<ContextCache> cacheCaptor = ArgumentCaptor.forClass(ContextCache.class);
        verify(contextCacheRepository).save(cacheCaptor.capture());
        assertThat(cacheCaptor.getValue().getVersion()).isEqualTo(4);
        assertThat(cacheCaptor.getValue().getEndSequenceNo()).isEqualTo(16L);
        assertThat(cacheCaptor.getValue().getCompressedUntilCreatedAt()).isEqualTo(OLD_CREATED_AT.plusSeconds(12));
    }

    @Test
    void compressIfNeeded_skipsUtterancesInsideGraceWindow() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(List.of());

        rollingSummaryService.compressIfNeeded(1L);

        verify(contextCacheRepository, never()).save(any());
        verify(aiChatService, never()).generateAnswer(any(), any());
    }

    @Test
    void compressIfNeeded_lateLowSequenceIsIncludedWhenCreatedAtIsEligible() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        List<Utterance> utterances = new ArrayList<>();
        utterances.add(utterance(meeting, 100L, 100, OLD_CREATED_AT.plusSeconds(1)));
        utterances.add(utterance(meeting, 95L, 100, OLD_CREATED_AT.plusSeconds(2)));
        for (long i = 101; i <= 130; i++) {
            utterances.add(utterance(meeting, i, 100, OLD_CREATED_AT.plusSeconds(i - 98)));
        }
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(utterances);
        given(aiChatService.generateAnswer(any(), any())).willReturn("late summary");

        rollingSummaryService.compressIfNeeded(1L);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateAnswer(any(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("#95");
    }

    @Test
    void compressIfNeeded_legacyCacheFallsBackToEndSequenceNo() {
        Meeting meeting = meeting();
        ContextCache latest = ContextCache.builder()
                .meeting(meeting)
                .version(1)
                .compressedText("legacy")
                .startSequenceNo(1L)
                .endSequenceNo(10L)
                .tokenCount(30)
                .build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(latest));
        given(utteranceRepository.findByMeetingAndSequenceNoGreaterThanAndCreatedAtLessThanEqualOrderBySequenceNoAsc(
                        meeting, 10L, SAFE_UNTIL))
                .willReturn(List.of());

        rollingSummaryService.compressIfNeeded(1L);

        verify(utteranceRepository)
                .findByMeetingAndSequenceNoGreaterThanAndCreatedAtLessThanEqualOrderBySequenceNoAsc(
                        meeting, 10L, SAFE_UNTIL);
    }

    @Test
    void calculateUncompressedTokenCountUsesSameGraceWindow() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(List.of(utterance(meeting, 1L, 100), utterance(meeting, 2L, null)));

        long result = rollingSummaryService.calculateUncompressedTokenCount(1L);

        assertThat(result).isEqualTo(100L);
    }

    @Test
    void compressIfNeeded_swallowsGlmException() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        List<Utterance> utterances = utterances(meeting, 1, 31, OLD_CREATED_AT);
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(utterances);
        given(aiChatService.generateAnswer(any(), any())).willThrow(new RuntimeException("glm failed"));

        assertThatCode(() -> rollingSummaryService.compressIfNeeded(1L)).doesNotThrowAnyException();
        verify(contextCacheRepository, never()).save(any());
    }

    private Meeting meeting() {
        return Meeting.builder().title("meeting").build();
    }

    private List<Utterance> utterances(Meeting meeting, int start, int end, LocalDateTime createdAtStart) {
        List<Utterance> utterances = new ArrayList<>();
        for (long i = start; i <= end; i++) {
            utterances.add(utterance(meeting, i, 100, createdAtStart.plusSeconds(i - start + 1)));
        }
        return utterances;
    }

    private Utterance utterance(Meeting meeting, Long sequenceNo, Integer tokenCount) {
        return utterance(meeting, sequenceNo, tokenCount, OLD_CREATED_AT.plusSeconds(sequenceNo));
    }

    private Utterance utterance(Meeting meeting, Long sequenceNo, Integer tokenCount, LocalDateTime createdAt) {
        return Utterance.builder()
                .meeting(meeting)
                .speakerName("speaker")
                .speakerDiscordId("discord")
                .content("content-" + sequenceNo)
                .spokenAt(LocalDateTime.of(2026, 5, 4, 10, 0).plusSeconds(sequenceNo))
                .sequenceNo(sequenceNo)
                .tokenCount(tokenCount)
                .createdAt(createdAt)
                .build();
    }
}
