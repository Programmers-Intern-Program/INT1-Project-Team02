package com.flodiback.domain.meeting.meetinglog.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.decision.decision.service.DecisionEmbeddingService;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.ContextResponse;
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
    private static final int UNCOMPRESSED_TOKEN_BUDGET = 2000;
    private static final int MIN_RECENT_UTTERANCE_COUNT = 20;

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

    public ContextResponse assemble(Long meetingId, String question) {
        Meeting meeting = meetingRepository
                .findById(meetingId)
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));

        ShortTermParts shortTerm = resolveShortTerm(meeting);

        Project project = meeting.getProject();
        if (project == null) {
            return ContextResponse.noProject(shortTerm.rollingSummary(), shortTerm.recentUtterances());
        }

        List<Decision> decisions = resolveDecisions(project.getId(), question);
        List<MeetingSummary> pastSummaries = resolvePastSummaries(project.getId(), meetingId, question);

        return ContextResponse.of(
                project, shortTerm.rollingSummary(), shortTerm.recentUtterances(), decisions, pastSummaries);
    }

    private ShortTermParts resolveShortTerm(Meeting meeting) {
        ContextCache latestCache = contextCacheRepository
                .findTopByMeetingOrderByVersionDesc(meeting)
                .orElse(null);
        if (latestCache == null) {
            List<Utterance> utterances = utteranceRepository.findByMeetingOrderByIdAsc(meeting);
            return new ShortTermParts(null, sortForPrompt(fitToTokenBudget(utterances)));
        }

        List<Utterance> utterancesAfterCache = utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdAsc(
                meeting, latestCache.getCompressedUntilUtteranceId());
        return new ShortTermParts(
                latestCache.getCompressedText(), sortForPrompt(fitToTokenBudget(utterancesAfterCache)));
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
                            .task(item.task())
                            .dueDate(item.dueDate())
                            .build()));
        }
    }

    private List<Decision> resolveDecisions(Long projectId, String question) {
        if (question == null || question.isBlank()) {
            return decisionRepository.findByProjectIdOrderByIdAsc(projectId);
        }

        try {
            float[] raw = embeddingClient.embed(question);
            String embeddingStr = new PGvector(raw).getValue();
            return decisionRepository.hybridSearch(
                    projectId, embeddingStr, question, TOP_K, SEMANTIC_WEIGHT, KEYWORD_WEIGHT);
        } catch (Exception e) {
            log.warn("하이브리드 서치 실패, 전체 결정사항으로 폴백 - projectId={}: {}", projectId, e.getMessage());
            return decisionRepository.findByProjectIdOrderByIdAsc(projectId);
        }
    }

    private List<MeetingSummary> resolvePastSummaries(Long projectId, Long meetingId, String question) {
        if (question == null || question.isBlank()) {
            return meetingSummaryRepository.findLatestPastByProjectId(projectId, meetingId, TOP_K);
        }

        try {
            float[] raw = embeddingClient.embed(question);
            String embeddingStr = new PGvector(raw).getValue();
            return meetingSummaryRepository.hybridSearch(
                    projectId, meetingId, embeddingStr, question, TOP_K, SEMANTIC_WEIGHT, KEYWORD_WEIGHT);
        } catch (Exception e) {
            log.warn("회의 요약 하이브리드 서치 실패, 최신 요약으로 폴백 - projectId={}: {}", projectId, e.getMessage());
            return meetingSummaryRepository.findLatestPastByProjectId(projectId, meetingId, TOP_K);
        }
    }

    private record ShortTermParts(String rollingSummary, List<Utterance> recentUtterances) {}
}
