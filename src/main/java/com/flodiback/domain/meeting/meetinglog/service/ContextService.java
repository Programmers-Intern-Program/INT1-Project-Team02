package com.flodiback.domain.meeting.meetinglog.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.decision.decision.service.DecisionEmbeddingService;
import com.flodiback.domain.meeting.meeting.context.MeetingStartContext;
import com.flodiback.domain.meeting.meeting.context.MeetingStartContextProvider;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.ContextResponse;
import com.flodiback.domain.meeting.meetinglog.dto.DecisionSummary;
import com.flodiback.domain.meeting.meetinglog.dto.PastSummary;
import com.flodiback.domain.meeting.meetinglog.dto.QuestionContext;
import com.flodiback.domain.meeting.meetinglog.dto.UpdateContextRequest;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.MeetingSummaryRepository;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.domain.project.worklog.entity.WorkLog;
import com.flodiback.domain.project.worklog.repository.WorkLogRepository;
import com.flodiback.global.embedding.OpenAiEmbeddingClient;
import com.flodiback.global.exception.ServiceException;
import com.flodiback.global.util.TokenEstimator;
import com.pgvector.PGvector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContextService {

    private static final double SEMANTIC_WEIGHT = 0.7;
    private static final double KEYWORD_WEIGHT = 0.3;
    private static final int TOP_K = 5;
    private static final int RECENT_DB_LIMIT = 60;
    private static final int UNCOMPRESSED_TOKEN_BUDGET = 2200;
    private static final int MIN_RECENT_UTTERANCE_COUNT = 16;

    private final MeetingRepository meetingRepository;
    private final UtteranceRepository utteranceRepository;
    private final ContextCacheRepository contextCacheRepository;
    private final DecisionRepository decisionRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final ProjectRepository projectRepository;
    private final WorkLogRepository workLogRepository;
    private final OpenAiEmbeddingClient embeddingClient;
    private final DecisionEmbeddingService decisionEmbeddingService;
    private final MeetingSummaryEmbeddingService meetingSummaryEmbeddingService;
    private final MeetingStartContextProvider meetingStartContextProvider;

    public ContextResponse assemble(Long meetingId, String question) {
        Meeting meeting = meetingRepository
                .findById(meetingId)
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));

        MeetingStartContext startContext = meetingStartContextProvider.getOrCreate(meetingId);
        ShortTermParts shortTerm = resolveShortTerm(meeting);

        Project project = meeting.getProject();
        if (project == null) {
            return ContextResponse.noProject(startContext, shortTerm.rollingSummary(), shortTerm.recentUtterances());
        }

        QuestionContext questionContext = resolveQuestionContext(project.getId(), meetingId, question, startContext);

        return ContextResponse.of(
                startContext, shortTerm.rollingSummary(), shortTerm.recentUtterances(), questionContext);
    }

    private ShortTermParts resolveShortTerm(Meeting meeting) {
        long startedAtNanos = System.nanoTime();
        ContextCache latestCache = contextCacheRepository
                .findTopByMeetingOrderByVersionDesc(meeting)
                .orElse(null);
        if (latestCache == null) {
            List<Utterance> fetched =
                    utteranceRepository.findByMeetingOrderByIdDesc(meeting, PageRequest.of(0, RECENT_DB_LIMIT));
            List<Utterance> recentUtterances = selectRecentUtterances(fetched);
            logShortTerm(meeting.getId(), startedAtNanos, false, fetched.size(), recentUtterances.size());
            return new ShortTermParts(null, recentUtterances);
        }

        List<Utterance> fetched = utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdDesc(
                meeting, latestCache.getCompressedUntilUtteranceId(), PageRequest.of(0, RECENT_DB_LIMIT));
        List<Utterance> recentUtterances = selectRecentUtterances(fetched);
        logShortTerm(meeting.getId(), startedAtNanos, true, fetched.size(), recentUtterances.size());
        return new ShortTermParts(latestCache.getCompressedText(), recentUtterances);
    }

    private List<Utterance> selectRecentUtterances(List<Utterance> utterances) {
        return fitToTokenBudget(sortForPrompt(utterances));
    }

    private void logShortTerm(
            Long meetingId, long startedAtNanos, boolean hasCache, int fetchedUtterances, int selectedUtterances) {
        log.info(
                "shortTerm context resolved. meetingId={}, elapsedMs={}, hasCache={}, fetchedUtterances={}, selectedUtterances={}",
                meetingId,
                elapsedMillis(startedAtNanos),
                hasCache,
                fetchedUtterances,
                selectedUtterances);
    }

    private List<Utterance> fitToTokenBudget(List<Utterance> utterances) {
        int tokenSum = 0;
        int selectedCount = 0;
        int start = utterances.size();

        for (int i = utterances.size() - 1; i >= 0; i--) {
            int tokens = estimateTokens(utterances.get(i));
            if (tokenSum + tokens > UNCOMPRESSED_TOKEN_BUDGET && selectedCount >= MIN_RECENT_UTTERANCE_COUNT) {
                break;
            }

            tokenSum += tokens;
            selectedCount++;
            start = i;
        }

        return utterances.subList(start, utterances.size());
    }

    private int estimateTokens(Utterance utterance) {
        return utterance.getTokenCount() != null
                ? utterance.getTokenCount()
                : TokenEstimator.estimate(utterance.getContent());
    }

    private List<Utterance> sortForPrompt(List<Utterance> utterances) {
        return utterances.stream()
                .sorted(Comparator.comparing(
                                Utterance::getSpeechStartedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Utterance::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public void updateContext(Long projectId, UpdateContextRequest req) {
        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ServiceException("404-2", "프로젝트를 찾을 수 없습니다."));

        Meeting meeting = meetingRepository
                .findById(req.meetingId())
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));
        if (meeting.getProject() == null
                || !projectId.equals(meeting.getProject().getId())) {
            throw new ServiceException("400-1", "회의가 요청한 프로젝트에 속하지 않습니다.");
        }

        MeetingSummary savedSummary = meetingSummaryRepository.save(MeetingSummary.builder()
                .meeting(meeting)
                .summary(req.summary())
                .unresolvedItems(req.unresolvedItems())
                .build());
        meetingSummaryEmbeddingService.processEmbedding(savedSummary);

        if (req.decisions() != null) {
            req.decisions().forEach(content -> {
                Decision saved = decisionRepository.save(Decision.builder()
                        .project(project)
                        .meeting(meeting)
                        .content(content)
                        .build());
                decisionEmbeddingService.processEmbedding(saved);
            });
        }

        if (req.actionItems() != null) {
            req.actionItems()
                    .forEach(item -> workLogRepository.save(WorkLog.builder()
                            .meeting(meeting)
                            .project(project)
                            .assigneeName(item.assigneeName())
                            .assigneeDiscordId(item.assigneeDiscordId())
                            .task(item.task())
                            .dueDate(item.dueDate())
                            .build()));
        }

        if (req.worklogUpdates() != null) {
            req.worklogUpdates().forEach(update -> workLogRepository
                    .findByIdAndProjectId(update.id(), projectId)
                    .ifPresent(wl -> {
                        wl.updateStatus(update.status());
                        workLogRepository.save(wl);
                    }));
        }
    }

    private QuestionContext resolveQuestionContext(
            Long projectId, Long meetingId, String question, MeetingStartContext startContext) {
        if (question == null || question.isBlank()) {
            return QuestionContext.empty();
        }

        String embeddingStr;
        try {
            long embeddingStartedAtNanos = System.nanoTime();
            float[] raw = embeddingClient.embed(question);
            embeddingStr = new PGvector(raw).getValue();
            log.info(
                    "questionContext embedding completed. projectId={}, meetingId={}, elapsedMs={}",
                    projectId,
                    meetingId,
                    elapsedMillis(embeddingStartedAtNanos));
        } catch (Exception e) {
            log.warn(
                    "questionContext embedding failed, returning empty context - projectId={}: {}",
                    projectId,
                    e.getMessage());
            return QuestionContext.empty();
        }

        List<DecisionSummary> decisions = resolveDecisions(projectId, question, embeddingStr, startContext).stream()
                .map(DecisionSummary::from)
                .toList();
        List<PastSummary> pastSummaries =
                resolvePastSummaries(projectId, meetingId, question, embeddingStr, startContext).stream()
                        .map(PastSummary::from)
                        .toList();

        return new QuestionContext(decisions, pastSummaries);
    }

    private List<Decision> resolveDecisions(
            Long projectId, String question, String embeddingStr, MeetingStartContext startContext) {
        long startedAtNanos = System.nanoTime();
        try {
            Set<Long> startDecisionIds = startContext.recentDecisions().stream()
                    .map(DecisionSummary::id)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            List<Decision> result =
                    decisionRepository
                            .hybridSearch(projectId, embeddingStr, question, TOP_K, SEMANTIC_WEIGHT, KEYWORD_WEIGHT)
                            .stream()
                            .filter(decision -> !startDecisionIds.contains(decision.getId()))
                            .toList();
            log.info(
                    "questionContext decision search completed. projectId={}, elapsedMs={}, resultCount={}",
                    projectId,
                    elapsedMillis(startedAtNanos),
                    result.size());
            return result;
        } catch (Exception e) {
            log.warn(
                    "questionContext decision search failed, returning empty context - projectId={}: {}",
                    projectId,
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<MeetingSummary> resolvePastSummaries(
            Long projectId, Long meetingId, String question, String embeddingStr, MeetingStartContext startContext) {
        long startedAtNanos = System.nanoTime();
        try {
            Set<Long> startSummaryIds = startContext.recentSummaries().stream()
                    .map(PastSummary::id)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            List<MeetingSummary> result = meetingSummaryRepository
                    .hybridSearch(projectId, meetingId, embeddingStr, question, TOP_K, SEMANTIC_WEIGHT, KEYWORD_WEIGHT)
                    .stream()
                    .filter(summary -> !startSummaryIds.contains(summary.getId()))
                    .toList();
            log.info(
                    "questionContext summary search completed. projectId={}, meetingId={}, elapsedMs={}, resultCount={}",
                    projectId,
                    meetingId,
                    elapsedMillis(startedAtNanos),
                    result.size());
            return result;
        } catch (Exception e) {
            log.warn(
                    "questionContext summary search failed, returning empty context - projectId={}, meetingId={}: {}",
                    projectId,
                    meetingId,
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private record ShortTermParts(String rollingSummary, List<Utterance> recentUtterances) {}
}
