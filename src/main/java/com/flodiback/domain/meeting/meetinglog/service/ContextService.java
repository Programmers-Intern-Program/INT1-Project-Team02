package com.flodiback.domain.meeting.meetinglog.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
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
import com.flodiback.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContextService {

    private final MeetingRepository meetingRepository;
    private final UtteranceRepository utteranceRepository;
    private final DecisionRepository decisionRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final ProjectRepository projectRepository;
    private final WorkLogRepository workLogRepository;

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

        // question 파라미터는 RAG 도입 시 유사도 검색에 활용 예정
        List<Decision> decisions = decisionRepository.findByProjectId(project.getId());
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
            req.decisions()
                    .forEach(content -> decisionRepository.save(Decision.builder()
                            .project(project)
                            .meeting(meeting)
                            .content(content)
                            .embedding(null) // RAG 구현 시 임베딩 생성 추가 예정
                            .build()));
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
}
