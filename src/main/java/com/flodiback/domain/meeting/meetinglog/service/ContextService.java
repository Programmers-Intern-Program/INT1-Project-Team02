package com.flodiback.domain.meeting.meetinglog.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.decision.decision.service.DecisionEmbeddingService;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
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

    private final MeetingRepository meetingRepository;
    private final UtteranceRepository utteranceRepository;
    private final DecisionRepository decisionRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final ProjectRepository projectRepository;
    private final WorkLogRepository workLogRepository;
    private final OpenAiEmbeddingClient embeddingClient;
    private final DecisionEmbeddingService decisionEmbeddingService;

    public ContextResponse assemble(Long meetingId, String question) {
        Meeting meeting = meetingRepository
                .findById(meetingId)
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));

        List<Utterance> recentUtterances = utteranceRepository.findTop20ByMeetingIdOrderBySpokenAtDesc(meetingId);
        Collections.reverse(recentUtterances);

        Project project = meeting.getProject();
        if (project == null) {
            return ContextResponse.noProject(recentUtterances);
        }

        List<Decision> decisions = resolveDecisions(project.getId(), question);
        List<MeetingSummary> pastSummaries = meetingSummaryRepository.findPastByProjectId(project.getId(), meetingId);

        return ContextResponse.of(project, recentUtterances, decisions, pastSummaries);
    }

    @Transactional
    public void updateContext(Long projectId, UpdateContextRequest req) {
        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ServiceException("404-2", "프로젝트를 찾을 수 없습니다."));

        Meeting meeting = meetingRepository
                .findById(req.meetingId())
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));

        meetingSummaryRepository.save(MeetingSummary.builder()
                .meeting(meeting)
                .summary(req.summary())
                .unresolvedItems(req.unresolvedItems())
                .build());

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
}
