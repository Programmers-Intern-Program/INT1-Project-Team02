package com.flodiback.domain.meeting.meeting.context;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.DecisionSummary;
import com.flodiback.domain.meeting.meetinglog.dto.PastSummary;
import com.flodiback.domain.meeting.meetinglog.repository.MeetingSummaryRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InMemoryMeetingStartContextProvider implements MeetingStartContextProvider {

    private static final int START_CONTEXT_LIMIT = 5;

    private final ConcurrentHashMap<Long, MeetingStartContext> cache = new ConcurrentHashMap<>();
    private final MeetingRepository meetingRepository;
    private final DecisionRepository decisionRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;

    @Override
    @Transactional(readOnly = true)
    public MeetingStartContext getOrCreate(Long meetingId) {
        return cache.computeIfAbsent(meetingId, this::buildContext);
    }

    @Override
    public void invalidate(Long meetingId) {
        cache.remove(meetingId);
    }

    private MeetingStartContext buildContext(Long meetingId) {
        Meeting meeting = meetingRepository
                .findById(meetingId)
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));
        Project project = meeting.getProject();
        if (project == null) {
            return MeetingStartContext.noProject();
        }

        List<DecisionSummary> recentDecisions =
                decisionRepository.findTop5ByProjectIdOrderByIdDesc(project.getId()).stream()
                        .sorted(java.util.Comparator.comparing(
                                com.flodiback.domain.decision.decision.entity.Decision::getId))
                        .map(DecisionSummary::from)
                        .toList();
        List<PastSummary> recentSummaries =
                meetingSummaryRepository
                        .findLatestPastByProjectId(project.getId(), meetingId, START_CONTEXT_LIMIT)
                        .stream()
                        .map(PastSummary::from)
                        .toList();

        return new MeetingStartContext(
                project.getName(), project.getTechStack(), project.getMetadata(), recentDecisions, recentSummaries);
    }
}
