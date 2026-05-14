package com.flodiback.domain.meeting.meetinglog.rolling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;

@ExtendWith(MockitoExtension.class)
class RollingSummaryPersistenceServiceTest {

    private static final LocalDateTime SAME_CREATED_AT = LocalDateTime.of(2026, 5, 4, 11, 59, 0);

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private UtteranceRepository utteranceRepository;

    @Mock
    private ContextCacheRepository contextCacheRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RollingSummaryPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new RollingSummaryPersistenceService(
                meetingRepository, utteranceRepository, contextCacheRepository, eventPublisher);
    }

    @Test
    void prepareCompression_returnsEmptyWhenEligibleTokensBelowThreshold() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting))
                .willReturn(List.of(utterance(meeting, 1L, 100), utterance(meeting, 2L, 200)));

        assertThat(service.prepareCompression(1L)).isEmpty();
    }

    @Test
    void prepareCompression_returnsEmptyWhenNoCompressibleTurnsRemainAfterTail() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(utterances(meeting, 1, 8, 200));

        assertThat(service.prepareCompression(1L)).isEmpty();
    }

    @Test
    void prepareCompression_buildsCandidateExceptLatestTail() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(utterances(meeting, 1, 31, 100));

        RollingSummaryPersistenceService.CompressionCandidate candidate =
                service.prepareCompression(1L).orElseThrow();

        assertThat(candidate.expectedVersion()).isNull();
        assertThat(candidate.expectedCompressedUntilUtteranceId()).isNull();
        assertThat(candidate.compressedUntilUtteranceId()).isEqualTo(23L);
        assertThat(candidate.userPrompt()).contains("content-1").doesNotContain("content-31");
        assertThat(candidate.userPrompt())
                .contains("[출력]")
                .contains("[흐름 요약]")
                .contains("[현재 회의 결정사항]")
                .contains("[미결 사항]")
                .contains("[액션 아이템]")
                .contains("보존")
                .contains("병합");
        assertThat(candidate.nextVersion()).isEqualTo(1);
    }

    @Test
    void prepareCompression_usesPreviousSummaryAndIdWatermark() {
        Meeting meeting = meeting();
        ContextCache latest = ContextCache.builder()
                .meeting(meeting)
                .version(3)
                .compressedText("previous summary")
                .tokenCount(30)
                .compressedUntilUtteranceId(5L)
                .build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(latest));
        given(utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdAsc(meeting, 5L))
                .willReturn(utterances(meeting, 6, 36, 100));

        RollingSummaryPersistenceService.CompressionCandidate candidate =
                service.prepareCompression(1L).orElseThrow();

        assertThat(candidate.expectedVersion()).isEqualTo(3);
        assertThat(candidate.expectedCompressedUntilUtteranceId()).isEqualTo(5L);
        assertThat(candidate.compressedUntilUtteranceId()).isEqualTo(28L);
        assertThat(candidate.userPrompt()).contains("previous summary");
        assertThat(candidate.nextVersion()).isEqualTo(4);
    }

    @Test
    void prepareCompression_sameCreatedAtUtterances_areNotLostWithIdWatermark() {
        Meeting meeting = meeting();
        ContextCache latest = ContextCache.builder()
                .meeting(meeting)
                .version(1)
                .compressedText("previous summary")
                .tokenCount(10)
                .compressedUntilUtteranceId(2L)
                .build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(latest));
        given(utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdAsc(meeting, 2L))
                .willReturn(utterances(meeting, 3, 33, 100));

        RollingSummaryPersistenceService.CompressionCandidate candidate =
                service.prepareCompression(1L).orElseThrow();

        assertThat(candidate.compressedUntilUtteranceId()).isEqualTo(25L);
        assertThat(candidate.userPrompt())
                .contains("content-3")
                .contains("content-25")
                .doesNotContain("content-33");
    }

    @Test
    void saveCompression_skipsWhenCacheAdvanced() {
        Meeting meeting = meeting();
        ContextCache latest = ContextCache.builder()
                .meeting(meeting)
                .version(1)
                .compressedText("already saved")
                .tokenCount(10)
                .compressedUntilUtteranceId(20L)
                .build();
        given(meetingRepository.findByIdForUpdate(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(latest));

        service.saveCompression(candidate(null, null), "summary");

        verify(contextCacheRepository, never()).save(any());
    }

    @Test
    void saveCompression_savesWhenCacheStillMatches() {
        Meeting meeting = meeting();
        given(meetingRepository.findByIdForUpdate(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(contextCacheRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.saveCompression(candidate(null, null), "summary");

        ArgumentCaptor<ContextCache> cacheCaptor = ArgumentCaptor.forClass(ContextCache.class);
        verify(contextCacheRepository).save(cacheCaptor.capture());
        ContextCache saved = cacheCaptor.getValue();
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getCompressedText()).isEqualTo("summary");
        assertThat(saved.getCompressedUntilUtteranceId()).isEqualTo(11L);
        verify(eventPublisher).publishEvent(new RollingSummaryUpdatedEvent(1L, "summary", 1));
    }

    private RollingSummaryPersistenceService.CompressionCandidate candidate(
            Integer expectedVersion, Long expectedWatermark) {
        return new RollingSummaryPersistenceService.CompressionCandidate(
                1L, expectedVersion, expectedWatermark, 11L, "prompt");
    }

    private Meeting meeting() {
        return Meeting.builder().title("meeting").build();
    }

    private List<Utterance> utterances(Meeting meeting, int start, int end, int tokenCount) {
        List<Utterance> utterances = new ArrayList<>();
        for (long i = start; i <= end; i++) {
            utterances.add(utterance(meeting, i, tokenCount));
        }
        return utterances;
    }

    private Utterance utterance(Meeting meeting, Long id, Integer tokenCount) {
        Utterance utterance = Utterance.builder()
                .meeting(meeting)
                .speakerName("speaker")
                .speakerDiscordId("discord")
                .content("content-" + id)
                .speechStartedAt(LocalDateTime.of(2026, 5, 4, 10, 0).plusSeconds(id))
                .tokenCount(tokenCount)
                .createdAt(SAME_CREATED_AT)
                .build();
        ReflectionTestUtils.setField(utterance, "id", id);
        return utterance;
    }
}
