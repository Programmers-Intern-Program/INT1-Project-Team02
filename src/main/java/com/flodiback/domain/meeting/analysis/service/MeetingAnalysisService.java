package com.flodiback.domain.meeting.analysis.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.domain.meeting.analysis.dto.AnalysisResult;
import com.flodiback.domain.meeting.analysis.dto.DecisionItem;
import com.flodiback.domain.meeting.analysis.dto.WorkLogItem;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.event.MeetingEndedEvent;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.ActionItemRequest;
import com.flodiback.domain.meeting.meetinglog.dto.UpdateContextRequest;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.meeting.meetinglog.service.ContextService;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.global.client.GlmClient;
import com.flodiback.global.exception.ServiceException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MeetingAnalysisService {

    @Value("classpath:prompts/meeting-analysis-system.md")
    private Resource systemPromptResource;

    private String systemPrompt;

    @PostConstruct
    private void loadPrompt() throws IOException {
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    private final MeetingRepository meetingRepository;
    private final ContextCacheRepository contextCacheRepository;
    private final UtteranceRepository utteranceRepository;
    private final ContextService contextService;
    private final GlmClient glmClient;
    private final ObjectMapper objectMapper;

    @Async("analysisExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMeetingEnded(MeetingEndedEvent event) {
        try {
            analyze(event.meetingId());
        } catch (Exception e) {
            log.warn("회의 분석 실패 - meetingId={}: {}", event.meetingId(), e.getMessage());
        }
    }

    public void analyze(Long meetingId) {
        Meeting meeting =
                meetingRepository.findById(meetingId).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회의입니다."));
        Project project = meeting.getProject();
        if (project == null) {
            throw new ServiceException("400-1", "회의에 연결된 프로젝트가 없습니다.");
        }

        String context = buildContext(meeting);
        AnalysisResult result = callGlm(context);

        contextService.updateContext(project.getId(), toUpdateContextRequest(meeting.getId(), result));
    }

    private UpdateContextRequest toUpdateContextRequest(Long meetingId, AnalysisResult result) {
        List<String> decisions = result.decisions() == null
                ? null
                : result.decisions().stream().map(DecisionItem::content).toList();
        List<ActionItemRequest> actionItems = result.worklogs() == null
                ? null
                : result.worklogs().stream().map(this::toActionItemRequest).toList();

        return new UpdateContextRequest(meetingId, result.summary(), result.unresolvedItems(), decisions, actionItems);
    }

    private ActionItemRequest toActionItemRequest(WorkLogItem item) {
        return new ActionItemRequest(item.assigneeName(), item.task(), item.dueDate());
    }

    private String buildContext(Meeting meeting) {
        ContextCache latestCache = contextCacheRepository
                .findTopByMeetingOrderByVersionDesc(meeting)
                .orElse(null);
        List<Utterance> utterances = latestCache == null
                ? utteranceRepository.findByMeetingOrderByIdAsc(meeting)
                : utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdAsc(
                        meeting, latestCache.getCompressedUntilUtteranceId());

        StringBuilder sb = new StringBuilder();
        if (latestCache != null) {
            sb.append("[현재 회의 rolling summary]\n");
            sb.append(latestCache.getCompressedText()).append("\n\n");
        }

        sb.append(latestCache != null ? "[rolling summary에 아직 반영되지 않은 발화]\n" : "[회의 전체 내용]\n");
        sortForPrompt(utterances)
                .forEach(u -> sb.append(String.format("%s: %s\n", u.getSpeakerName(), u.getContent())));

        return sb.toString();
    }

    private List<Utterance> sortForPrompt(List<Utterance> utterances) {
        return utterances.stream()
                .sorted(Comparator.comparing(
                                Utterance::getSpeechStartedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Utterance::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private AnalysisResult callGlm(String context) {
        String raw = glmClient.chat(systemPrompt, context);
        String json = raw.strip()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "");

        try {
            return objectMapper.readValue(json, AnalysisResult.class);
        } catch (Exception e) {
            throw new RuntimeException("GLM 응답 파싱 실패: " + raw, e);
        }
    }
}
