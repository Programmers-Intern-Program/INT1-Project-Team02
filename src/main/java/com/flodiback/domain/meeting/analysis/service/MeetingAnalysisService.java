package com.flodiback.domain.meeting.analysis.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

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
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository.SpeakerProjection;
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

    private static final String UNKNOWN_ASSIGNEE = "담당자 미정";
    private static final String UNKNOWN_SPEAKER_KEY = "unknown";

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
    private final MeetingContextPersistenceService meetingContextPersistenceService;
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
        // 회의 종료 분석 - 발화자 key map 생성
        SpeakerDirectory speakerDirectory = buildSpeakerDirectory(meeting);
        // GLM prompt context 생성
        String context = buildContext(meeting, speakerDirectory);
        // GLM 응답 - GLM은 이름 대신 speaker key 반환
        AnalysisResult result = callGlm(context);
        UpdateContextRequest request = toUpdateContextRequest(meeting.getId(), result, speakerDirectory);

        /*
         * saveSummaryRequired()
         * -> MeetingSummary 먼저 REQUIRES_NEW로 저장
         *
         * saveSummaryEmbeddingBestEffort()
         * -> summary embedding 실패해도 summary는 유지
         *
         * saveDerivedItemsBestEffort()
         * -> decision/worklog 저장 실패해도 summary는 롤백하지 않음
         */
        // Keep these as external proxy calls; do not wrap them in a same-bean orchestration method.
        MeetingSummary savedSummary = meetingContextPersistenceService.saveSummaryRequired(project.getId(), request);
        meetingContextPersistenceService.saveSummaryEmbeddingBestEffort(savedSummary);
        meetingContextPersistenceService.saveDerivedItemsBestEffort(project.getId(), request);
    }

    private UpdateContextRequest toUpdateContextRequest(
            Long meetingId, AnalysisResult result, SpeakerDirectory speakerDirectory) {
        String summary = normalizeRequiredSummary(result.summary());
        List<String> decisions = result.decisions() == null
                ? null
                : result.decisions().stream()
                        .filter(Objects::nonNull)
                        .map(DecisionItem::content)
                        .map(this::normalizeOptionalText)
                        .filter(Objects::nonNull)
                        .toList();
        List<ActionItemRequest> actionItems = result.worklogs() == null
                ? null
                : result.worklogs().stream()
                        .map(item -> toActionItemRequest(item, speakerDirectory))
                        .filter(Objects::nonNull)
                        .toList();

        return new UpdateContextRequest(
                meetingId, summary, normalizeOptionalText(result.unresolvedItems()), decisions, actionItems);
    }

    private ActionItemRequest toActionItemRequest(WorkLogItem item, SpeakerDirectory speakerDirectory) {
        if (item == null) {
            return null;
        }
        String task = normalizeOptionalText(item.task());
        if (task == null) {
            return null;
        }

        SpeakerRef assignee = speakerDirectory.findByKey(normalizeOptionalText(item.assigneeSpeakerKey()));
        if (assignee == null) {
            return new ActionItemRequest(null, UNKNOWN_ASSIGNEE, task, item.dueDate());
        }
        return new ActionItemRequest(assignee.discordId(), assignee.name(), task, item.dueDate());
    }

    private String normalizeRequiredSummary(String summary) {
        String normalized = normalizeOptionalText(summary);
        if (normalized == null) {
            throw new ServiceException("422-1", "Meeting analysis summary is blank.");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isBlank() ? null : stripped;
    }

    /**
     * 회의 전체 발화자 목록을 조회
     * firstUtteranceId ASC 순서로 speaker key를 붙임
     * S1 -> Alice / discord-user-1
     * S2 -> Bob / discord-user-2
     *
     * speakerDiscordId가 없거나 blank인 발화자는 key map에서 제외
     */
    private SpeakerDirectory buildSpeakerDirectory(Meeting meeting) {
        Map<String, SpeakerRef> byDiscordId = new LinkedHashMap<>();
        Map<String, SpeakerRef> byKey = new LinkedHashMap<>();

        List<SpeakerProjection> speakers = utteranceRepository.findDistinctSpeakersByMeeting(meeting);

        int sequence = 1;
        for (SpeakerProjection speaker : speakers) {
            String discordId = normalizeOptionalText(speaker.getSpeakerDiscordId());
            if (byDiscordId.containsKey(discordId)) {
                continue;
            }
            String key = "S" + sequence++;
            String name = normalizeOptionalText(speaker.getSpeakerName());
            SpeakerRef ref = new SpeakerRef(key, discordId, name != null ? name : UNKNOWN_ASSIGNEE);
            byDiscordId.put(discordId, ref);
            byKey.put(key, ref);
        }

        return new SpeakerDirectory(byDiscordId, byKey);
    }

    /**
     * 분석 prompt에는 raw Discord ID를 넣지 않음
     * 대신 서버가 만든 speaker key만 넣음
     *
     * [회의 전체 내용]
     * [speaker=S1, name=Alice] API 명세 확정했어요.
     * [speaker=S2, name=Bob] 제가 금요일까지 구현할게요.
     *
     * rolling summary가 있으면:
     * [현재 회의 rolling summary]
     * ...
     *
     * [rolling summary에 아직 반영되지 않은 발화]
     * [speaker=S2, name=Bob] 제가 금요일까지 구현할게요.
     *
     * rolling summary에는 speaker key가 없다.
     * 그래서 prompt에서도 summary에서만 추론한 담당자는 assigneeSpeakerKey=null로 반환
     */
    private String buildContext(Meeting meeting, SpeakerDirectory speakerDirectory) {
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
        sortForPrompt(utterances).forEach(u -> sb.append(formatUtterance(u, speakerDirectory)));

        return sb.toString();
    }

    private String formatUtterance(Utterance utterance, SpeakerDirectory speakerDirectory) {
        SpeakerRef speaker = speakerDirectory.findByDiscordId(normalizeOptionalText(utterance.getSpeakerDiscordId()));
        String key = speaker != null ? speaker.key() : UNKNOWN_SPEAKER_KEY;
        String name = normalizeOptionalText(utterance.getSpeakerName());
        return String.format(
                "[speaker=%s, name=%s] %s\n", key, name != null ? name : UNKNOWN_ASSIGNEE, utterance.getContent());
    }

    private List<Utterance> sortForPrompt(List<Utterance> utterances) {
        return utterances.stream()
                .sorted(Comparator.comparing(
                                Utterance::getSpeechStartedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Utterance::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private AnalysisResult callGlm(String context) {
        String prompt = systemPrompt.replace("{today}", LocalDate.now().toString());
        String raw = glmClient.chat(prompt, context);
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

    private record SpeakerDirectory(Map<String, SpeakerRef> byDiscordId, Map<String, SpeakerRef> byKey) {

        SpeakerRef findByDiscordId(String discordId) {
            return discordId != null ? byDiscordId.get(discordId) : null;
        }

        SpeakerRef findByKey(String key) {
            return key != null ? byKey.get(key) : null;
        }
    }

    private record SpeakerRef(String key, String discordId, String name) {}
}
