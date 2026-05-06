package com.flodiback.domain.meeting.meetinglog.rolling;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    private static final int GRACE_SECONDS = 10;

    private final MeetingRepository meetingRepository;
    private final UtteranceRepository utteranceRepository;
    private final ContextCacheRepository contextCacheRepository;
    private final Clock clock;

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
        List<Utterance> uncompressed = findCompressibleUtterances(meeting, latestCache, safeUntil());
        long tokenSum = sumTokens(uncompressed);
        if (tokenSum < RollingSummaryService.TOKEN_THRESHOLD) {
            return Optional.empty();
        }

        int compressEndExclusive = Math.max(0, uncompressed.size() - RollingSummaryService.KEEP_TURNS);
        if (compressEndExclusive == 0) {
            return Optional.empty();
        }

        List<Utterance> toCompress = uncompressed.subList(0, compressEndExclusive);
        LocalDateTime compressedUntilCreatedAt = toCompress.stream()
                .map(Utterance::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return Optional.of(new CompressionCandidate(
                meetingId,
                latestCache != null ? latestCache.getVersion() : null,
                latestCache != null ? latestCache.getCompressedUntilCreatedAt() : null,
                compressedUntilCreatedAt,
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
        LocalDateTime actualWatermark = latestCache != null ? latestCache.getCompressedUntilCreatedAt() : null;
        if (!Objects.equals(candidate.expectedVersion(), actualVersion)
                || !Objects.equals(candidate.expectedCompressedUntilCreatedAt(), actualWatermark)) {
            log.info("Rolling summary save skipped because cache advanced. meetingId={}", candidate.meetingId());
            return;
        }

        contextCacheRepository.save(ContextCache.builder()
                .meeting(meeting)
                .version(candidate.nextVersion())
                .compressedText(compressedText.strip())
                .tokenCount(TokenEstimator.estimate(compressedText))
                .compressedUntilCreatedAt(candidate.compressedUntilCreatedAt())
                .build());
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
        return sumTokens(findCompressibleUtterances(meeting, latestCache, safeUntil()));
    }

    private List<Utterance> findCompressibleUtterances(
            Meeting meeting, ContextCache latestCache, LocalDateTime safeUntil) {
        if (latestCache == null) {
            return utteranceRepository.findByMeetingAndCreatedAtLessThanEqualOrderByCreatedAtAsc(meeting, safeUntil);
        }

        return utteranceRepository.findByMeetingAndCreatedAtGreaterThanAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                meeting, latestCache.getCompressedUntilCreatedAt(), safeUntil);
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

    private LocalDateTime safeUntil() {
        return LocalDateTime.now(clock).minusSeconds(GRACE_SECONDS);
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
        prompt.append("\n[출력]\n현재 회의 rolling summary만 작성하세요.");
        return prompt.toString();
    }

    record CompressionCandidate(
            Long meetingId,
            Integer expectedVersion,
            LocalDateTime expectedCompressedUntilCreatedAt,
            LocalDateTime compressedUntilCreatedAt,
            String userPrompt) {

        int nextVersion() {
            return expectedVersion != null ? expectedVersion + 1 : 1;
        }
    }
}
