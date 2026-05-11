package com.flodiback.domain.meeting.meetinglog.rolling;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.global.util.TokenEstimator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RollingSummaryPersistenceService {

    private final MeetingRepository meetingRepository;
    private final UtteranceRepository utteranceRepository;
    private final ContextCacheRepository contextCacheRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Optional<CompressionCandidate> prepareCompression(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
        if (meeting == null) {
            log.warn("Rolling summary skipped because meeting does not exist. meetingId={}", meetingId);
            return Optional.empty();
        }

        ContextCache latestCache = contextCacheRepository
                .findTopByMeetingOrderByVersionDesc(meeting)
                .orElse(null);
        List<Utterance> uncompressed = findCompressibleUtterances(meeting, latestCache);
        long tokenSum = sumTokens(uncompressed);
        if (tokenSum < RollingSummaryService.TOKEN_THRESHOLD) {
            return Optional.empty();
        }

        int compressEndExclusive = Math.max(0, uncompressed.size() - RollingSummaryService.KEEP_TURNS);
        if (compressEndExclusive == 0) {
            return Optional.empty();
        }

        List<Utterance> toCompress = uncompressed.subList(0, compressEndExclusive);
        Long compressedUntilUtteranceId = toCompress.stream()
                .map(Utterance::getId)
                .max(Long::compareTo)
                .orElseThrow(() -> new IllegalStateException("Compressible utterance id must not be null."));

        return Optional.of(new CompressionCandidate(
                meetingId,
                latestCache != null ? latestCache.getVersion() : null,
                latestCache != null ? latestCache.getCompressedUntilUtteranceId() : null,
                compressedUntilUtteranceId,
                buildUserPrompt(
                        latestCache != null ? latestCache.getCompressedText() : null, sortForPrompt(toCompress))));
    }

    @Transactional
    public void saveCompression(CompressionCandidate candidate, String compressedText) {
        Meeting meeting =
                meetingRepository.findByIdForUpdate(candidate.meetingId()).orElse(null);
        if (meeting == null) {
            log.warn(
                    "Rolling summary save skipped because meeting does not exist. meetingId={}", candidate.meetingId());
            return;
        }

        ContextCache latestCache = contextCacheRepository
                .findTopByMeetingOrderByVersionDesc(meeting)
                .orElse(null);
        Integer actualVersion = latestCache != null ? latestCache.getVersion() : null;
        Long actualWatermark = latestCache != null ? latestCache.getCompressedUntilUtteranceId() : null;
        if (!Objects.equals(candidate.expectedVersion(), actualVersion)
                || !Objects.equals(candidate.expectedCompressedUntilUtteranceId(), actualWatermark)) {
            log.info("Rolling summary save skipped because cache advanced. meetingId={}", candidate.meetingId());
            return;
        }

        ContextCache saved = contextCacheRepository.save(ContextCache.builder()
                .meeting(meeting)
                .version(candidate.nextVersion())
                .compressedText(compressedText.strip())
                .tokenCount(TokenEstimator.estimate(compressedText))
                .compressedUntilUtteranceId(candidate.compressedUntilUtteranceId())
                .build());

        eventPublisher.publishEvent(
                new RollingSummaryUpdatedEvent(candidate.meetingId(), saved.getCompressedText(), saved.getVersion()));
    }

    @Transactional(readOnly = true)
    public Optional<RollingSummarySnapshot> getLatestSummary(Long meetingId) {
        return meetingRepository
                .findById(meetingId)
                .flatMap(meeting -> contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .map(cache -> new RollingSummarySnapshot(meetingId, cache.getCompressedText(), cache.getVersion()));
    }

    @Transactional(readOnly = true)
    public long calculateUncompressedTokenCount(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
        if (meeting == null) {
            return 0;
        }

        ContextCache latestCache = contextCacheRepository
                .findTopByMeetingOrderByVersionDesc(meeting)
                .orElse(null);
        return sumTokens(findCompressibleUtterances(meeting, latestCache));
    }

    private List<Utterance> findCompressibleUtterances(Meeting meeting, ContextCache latestCache) {
        if (latestCache == null) {
            return utteranceRepository.findByMeetingOrderByIdAsc(meeting);
        }

        return utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdAsc(
                meeting, latestCache.getCompressedUntilUtteranceId());
    }

    private long sumTokens(List<Utterance> utterances) {
        return utterances.stream()
                .mapToLong(utterance -> utterance.getTokenCount() != null ? utterance.getTokenCount() : 0L)
                .sum();
    }

    private List<Utterance> sortForPrompt(List<Utterance> utterances) {
        return utterances.stream()
                .sorted(Comparator.comparing(
                                Utterance::getSpeechStartedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Utterance::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private String buildUserPrompt(String previousSummary, List<Utterance> utterances) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("[이전 rolling summary]\n");
        prompt.append(StringUtils.hasText(previousSummary) ? previousSummary.strip() : "- 없음");
        prompt.append("\n\n[새로 압축할 발화]\n");
        utterances.forEach(utterance -> prompt.append("- [")
                .append(utterance.getSpeakerName())
                .append("] ")
                .append(utterance.getContent())
                .append("\n"));
        prompt.append("""

                [출력]
                [흐름 요약], [현재 회의 결정사항], [미결 사항], [액션 아이템] 섹션으로 구분해 작성하세요.
                이전 rolling summary에 있던 항목은 보존하고 새 발화에서 나온 항목을 병합하세요.
                새 발화에서 기존 항목이 취소되거나 변경되면 최신 발화를 우선하세요.
                내용이 없는 섹션은 "- 없음"으로 채우세요.
                """);
        return prompt.toString();
    }

    public record RollingSummarySnapshot(Long meetingId, String summary, Integer version) {}

    record CompressionCandidate(
            Long meetingId,
            Integer expectedVersion,
            Long expectedCompressedUntilUtteranceId,
            Long compressedUntilUtteranceId,
            String userPrompt) {

        int nextVersion() {
            return expectedVersion != null ? expectedVersion + 1 : 1;
        }
    }
}
