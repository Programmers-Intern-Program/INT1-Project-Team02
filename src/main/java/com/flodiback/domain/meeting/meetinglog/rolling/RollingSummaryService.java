package com.flodiback.domain.meeting.meetinglog.rolling;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.flodiback.domain.ai.service.AiChatService;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RollingSummaryService {

    private static final String SYSTEM_PROMPT = """
            당신은 회의 내용을 압축하는 AI 요약기입니다.
            이전 요약이 있으면 유지해야 할 결정, 미결사항, 작업 맥락을 보존하고,
            새 발화에서 추가된 핵심만 반영해 현재 회의 rolling summary를 한국어로 갱신하세요.
            """;

    private final MeetingRepository meetingRepository;
    private final UtteranceRepository utteranceRepository;
    private final ContextCacheRepository contextCacheRepository;
    private final AiChatService aiChatService;
    private final Clock clock;

    @Transactional
    public void compressIfNeeded(Long meetingId) {
        try {
            Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
            if (meeting == null) {
                log.warn("Rolling summary skipped because meeting does not exist. meetingId={}", meetingId);
                return;
            }

            ContextCache latestCache = contextCacheRepository
                    .findTopByMeetingOrderByVersionDesc(meeting)
                    .orElse(null);
            List<Utterance> uncompressed = findCompressibleUtterances(meeting, latestCache, safeUntil());
            long tokenSum = sumTokens(uncompressed);
            if (tokenSum < RollingSummaryStreamConstants.TOKEN_THRESHOLD) {
                return;
            }

            int compressEndExclusive = Math.max(0, uncompressed.size() - RollingSummaryStreamConstants.KEEP_TURNS);
            if (compressEndExclusive == 0) {
                return;
            }

            List<Utterance> toCompress = uncompressed.subList(0, compressEndExclusive);
            String previousSummary = latestCache != null ? latestCache.getCompressedText() : null;
            String userPrompt = buildUserPrompt(previousSummary, sortForPrompt(toCompress));
            String compressedText = aiChatService.generateAnswer(SYSTEM_PROMPT, userPrompt);
            if (!StringUtils.hasText(compressedText)) {
                log.warn("Rolling summary GLM returned blank result. meetingId={}", meetingId);
                return;
            }

            long startSequenceNo = toCompress.stream()
                    .mapToLong(Utterance::getSequenceNo)
                    .min()
                    .orElse(0L);
            long endSequenceNo = toCompress.stream()
                    .mapToLong(Utterance::getSequenceNo)
                    .max()
                    .orElse(0L);
            LocalDateTime compressedUntilCreatedAt = toCompress.stream()
                    .map(Utterance::getCreatedAt)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            int nextVersion = latestCache != null ? latestCache.getVersion() + 1 : 1;

            contextCacheRepository.save(ContextCache.builder()
                    .meeting(meeting)
                    .version(nextVersion)
                    .compressedText(compressedText.strip())
                    .startSequenceNo(startSequenceNo)
                    .endSequenceNo(endSequenceNo)
                    .tokenCount(estimateTokenCount(compressedText))
                    .compressedUntilCreatedAt(compressedUntilCreatedAt)
                    .build());
        } catch (RuntimeException e) {
            log.warn("Rolling summary compression failed. meetingId={}, reason={}", meetingId, e.getMessage());
        }
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

        if (latestCache.getCompressedUntilCreatedAt() != null) {
            return utteranceRepository.findByMeetingAndCreatedAtGreaterThanAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                    meeting, latestCache.getCompressedUntilCreatedAt(), safeUntil);
        }

        return utteranceRepository.findByMeetingAndSequenceNoGreaterThanAndCreatedAtLessThanEqualOrderBySequenceNoAsc(
                meeting, latestCache.getEndSequenceNo(), safeUntil);
    }

    private long sumTokens(List<Utterance> utterances) {
        return utterances.stream()
                .mapToLong(utterance -> utterance.getTokenCount() != null ? utterance.getTokenCount() : 0L)
                .sum();
    }

    private List<Utterance> sortForPrompt(List<Utterance> utterances) {
        return utterances.stream()
                .sorted(Comparator.comparing(Utterance::getSpokenAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Utterance::getSequenceNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Utterance::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private LocalDateTime safeUntil() {
        return LocalDateTime.now(clock).minusSeconds(RollingSummaryStreamConstants.GRACE_SECONDS);
    }

    private String buildUserPrompt(String previousSummary, List<Utterance> utterances) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("[이전 rolling summary]\n");
        prompt.append(StringUtils.hasText(previousSummary) ? previousSummary.strip() : "- 없음");
        prompt.append("\n\n[새로 압축할 발화]\n");
        utterances.forEach(utterance -> prompt.append("- #")
                .append(utterance.getSequenceNo())
                .append(" [")
                .append(utterance.getSpeakerName())
                .append("] ")
                .append(utterance.getContent())
                .append("\n"));
        prompt.append("\n[출력]\n현재 회의 rolling summary만 작성하세요.");
        return prompt.toString();
    }

    private int estimateTokenCount(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
