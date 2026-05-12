package com.flodiback.domain.meeting.analysis.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.decision.decision.service.DecisionEmbeddingService;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.UpdateContextRequest;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.repository.MeetingSummaryRepository;
import com.flodiback.domain.meeting.meetinglog.service.MeetingSummaryEmbeddingService;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.domain.project.worklog.entity.WorkLog;
import com.flodiback.domain.project.worklog.repository.WorkLogRepository;
import com.flodiback.global.exception.ServiceException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MeetingContextPersistenceService {

    private final MeetingRepository meetingRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final ProjectRepository projectRepository;
    private final WorkLogRepository workLogRepository;
    private final DecisionRepository decisionRepository;
    private final DecisionEmbeddingService decisionEmbeddingService;
    private final MeetingSummaryEmbeddingService meetingSummaryEmbeddingService;
    private final TransactionTemplate derivedItemsTransactionTemplate;

    public MeetingContextPersistenceService(
            MeetingRepository meetingRepository,
            MeetingSummaryRepository meetingSummaryRepository,
            ProjectRepository projectRepository,
            WorkLogRepository workLogRepository,
            DecisionRepository decisionRepository,
            DecisionEmbeddingService decisionEmbeddingService,
            MeetingSummaryEmbeddingService meetingSummaryEmbeddingService,
            PlatformTransactionManager transactionManager) {
        this.meetingRepository = meetingRepository;
        this.meetingSummaryRepository = meetingSummaryRepository;
        this.projectRepository = projectRepository;
        this.workLogRepository = workLogRepository;
        this.decisionRepository = decisionRepository;
        this.decisionEmbeddingService = decisionEmbeddingService;
        this.meetingSummaryEmbeddingService = meetingSummaryEmbeddingService;
        this.derivedItemsTransactionTemplate = new TransactionTemplate(transactionManager);
        this.derivedItemsTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MeetingSummary saveSummaryRequired(Long projectId, UpdateContextRequest req) {
        projectRepository.findById(projectId).orElseThrow(() -> new ServiceException("404-2", "프로젝트를 찾을 수 없습니다."));
        Meeting meeting = findMeetingInProject(projectId, req.meetingId());

        return meetingSummaryRepository.save(MeetingSummary.builder()
                .meeting(meeting)
                .summary(req.summary())
                .unresolvedItems(req.unresolvedItems())
                .build());
    }

    public void saveSummaryEmbeddingBestEffort(MeetingSummary savedSummary) {
        try {
            meetingSummaryEmbeddingService.processEmbedding(savedSummary);
        } catch (RuntimeException e) {
            log.warn(
                    "Meeting summary embedding failed after summary save - summaryId={}: {}",
                    savedSummary != null ? savedSummary.getId() : null,
                    e.getMessage());
        }
    }

    public void saveDerivedItemsBestEffort(Long projectId, UpdateContextRequest req) {
        try {
            derivedItemsTransactionTemplate.executeWithoutResult(status -> saveDerivedItems(projectId, req));
        } catch (Exception e) {
            log.warn(
                    "Meeting derived context save skipped after summary save - projectId={}, meetingId={}, reason={}",
                    projectId,
                    req != null ? req.meetingId() : null,
                    e.getMessage());
        }
    }

    private void saveDerivedItems(Long projectId, UpdateContextRequest req) {
        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ServiceException("404-2", "프로젝트를 찾을 수 없습니다."));
        Meeting meeting = findMeetingInProject(projectId, req.meetingId());

        if (req.decisions() != null) {
            req.decisions().forEach(content -> {
                Decision saved = decisionRepository.save(Decision.builder()
                        .project(project)
                        .meeting(meeting)
                        .content(content)
                        .build());
                // TODO: Move decision embedding outside this DB transaction in a follow-up.
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

        decisionRepository.flush();
        workLogRepository.flush();
    }

    private Meeting findMeetingInProject(Long projectId, Long meetingId) {
        Meeting meeting = meetingRepository
                .findById(meetingId)
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));
        if (meeting.getProject() == null
                || !projectId.equals(meeting.getProject().getId())) {
            throw new ServiceException("400-1", "회의가 요청한 프로젝트에 속하지 않습니다.");
        }
        return meeting;
    }
}
