package com.flodiback.domain.meeting.meetinglog.rolling;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;

@ExtendWith(MockitoExtension.class)
class RollingSummaryPersistenceServiceTest {

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

    private RollingSummaryPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new RollingSummaryPersistenceService(
                meetingRepository, utteranceRepository, contextCacheRepository, FIXED_CLOCK);
    }

    @Test
    void prepareCompression_returnsEmptyWhenEligibleTokensBelowThreshold() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(List.of(utterance(meeting, 1L, 100), utterance(meeting, 2L, 200)));

        assertThat(service.prepareCompression(1L)).isEmpty();
    }

    @Test
    void prepareCompression_returnsEmptyWhenNoCompressibleTurnsRemainAfterTail() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(utterances(meeting, 1, 20, OLD_CREATED_AT, 200));

        assertThat(service.prepareCompression(1L)).isEmpty();
    }

    @Test
    void prepareCompression_buildsCandidateExceptLatestTail() {
        Meeting meeting = meeting();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, SAFE_UNTIL))
                .willReturn(utterances(meeting, 1, 31, OLD_CREATED_AT, 100));

        RollingSummaryPersistenceService.CompressionCandidate candidate =
                service.prepareCompression(1L).orElseThrow();

        assertThat(candidate.expectedVersion()).isNull();
        assertThat(candidate.expectedCompressedUntilCreatedAt()).isNull();
        assertThat(candidate.compressedUntilCreatedAt()).isEqualTo(OLD_CREATED_AT.plusSeconds(11));
        assertThat(candidate.userPrompt()).contains("content-1").doesNotContain("content-31");
        assertThat(candidate.nextVersion()).isEqualTo(1);
    }

    @Test
    void prepareCompression_usesPreviousSummaryAndWatermark() {
        Meeting meeting = meeting();
        ContextCache latest = ContextCache.builder()
                .meeting(meeting)
                .version(3)
                .compressedText("previous summary")
                .tokenCount(30)
                .compressedUntilCreatedAt(OLD_CREATED_AT)
                .build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(latest));
        given(utteranceRepository.findByMeetingAndCreatedAtGreaterThanAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                        meeting, OLD_CREATED_AT, SAFE_UNTIL))
                .willReturn(utterances(meeting, 6, 36, OLD_CREATED_AT.plusSeconds(1), 100));

        RollingSummaryPersistenceService.CompressionCandidate candidate =
                service.prepareCompression(1L).orElseThrow();

        assertThat(candidate.expectedVersion()).isEqualTo(3);
        assertThat(candidate.expectedCompressedUntilCreatedAt()).isEqualTo(OLD_CREATED_AT);
        assertThat(candidate.compressedUntilCreatedAt()).isEqualTo(OLD_CREATED_AT.plusSeconds(12));
        assertThat(candidate.userPrompt()).contains("previous summary");
        assertThat(candidate.nextVersion()).isEqualTo(4);
    }

    @Test
    void saveCompression_skipsWhenCacheAdvanced() {
        Meeting meeting = meeting();
        ContextCache latest = ContextCache.builder()
                .meeting(meeting)
                .version(1)
                .compressedText("already saved")
                .tokenCount(10)
                .compressedUntilCreatedAt(OLD_CREATED_AT)
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

        service.saveCompression(candidate(null, null), "summary");

        ArgumentCaptor<ContextCache> cacheCaptor = ArgumentCaptor.forClass(ContextCache.class);
        verify(contextCacheRepository).save(cacheCaptor.capture());
        ContextCache saved = cacheCaptor.getValue();
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getCompressedText()).isEqualTo("summary");
        assertThat(saved.getCompressedUntilCreatedAt()).isEqualTo(OLD_CREATED_AT.plusSeconds(11));
    }

    private RollingSummaryPersistenceService.CompressionCandidate candidate(
            Integer expectedVersion, LocalDateTime expectedWatermark) {
        return new RollingSummaryPersistenceService.CompressionCandidate(
                1L, expectedVersion, expectedWatermark, OLD_CREATED_AT.plusSeconds(11), "prompt");
    }

    private Meeting meeting() {
        return Meeting.builder().title("meeting").build();
    }

    private List<Utterance> utterances(
            Meeting meeting, int start, int end, LocalDateTime createdAtStart, int tokenCount) {
        List<Utterance> utterances = new ArrayList<>();
        for (long i = start; i <= end; i++) {
            utterances.add(utterance(meeting, i, tokenCount, createdAtStart.plusSeconds(i - start + 1)));
        }
        return utterances;
    }

    private Utterance utterance(Meeting meeting, Long index, Integer tokenCount) {
        return utterance(meeting, index, tokenCount, OLD_CREATED_AT.plusSeconds(index));
    }

    private Utterance utterance(Meeting meeting, Long index, Integer tokenCount, LocalDateTime createdAt) {
        return Utterance.builder()
                .meeting(meeting)
                .speakerName("speaker")
                .speakerDiscordId("discord")
                .content("content-" + index)
                .speechStartedAt(LocalDateTime.of(2026, 5, 4, 10, 0).plusSeconds(index))
                .tokenCount(tokenCount)
                .createdAt(createdAt)
                .build();
    }
}
