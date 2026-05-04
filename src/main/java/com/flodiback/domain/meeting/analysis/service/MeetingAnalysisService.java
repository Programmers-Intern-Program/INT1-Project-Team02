package com.flodiback.domain.meeting.analysis.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.domain.meeting.analysis.dto.AnalysisResult;
import com.flodiback.domain.meeting.analysis.dto.DecisionItem;
import com.flodiback.domain.meeting.analysis.dto.WorkLogItem;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MeetingAnalysisService {

    private static final String SYSTEM_PROMPT = """
            당신은 회의 내용을 분석하는 어시스턴트입니다.
            아래 회의 내용을 분석해서 반드시 다음 JSON 형식으로만 응답하세요.
            마크다운 코드블록 없이 순수 JSON만 반환하세요.
            {
              "summary": "전체 회의 요약",
              "unresolvedItems": "미결 사항 (없으면 null)",
              "worklogs": [
                { "assigneeName": "담당자 이름", "task": "작업 내용", "dueDate": "YYYY-MM-DD" }
              ],
              "decisions": [
                { "content": "결정 내용" }
              ]
            }
            dueDate는 날짜가 있으면 "YYYY-MM-DD" 문자열로, 없으면 따옴표 없는 null로 반환하세요.
            """;

    private final MeetingRepository meetingRepository;
    private final ContextCacheRepository contextCacheRepository;
    private final UtteranceRepository utteranceRepository;
    private final ContextService contextService;
    private final GlmClient glmClient;
    private final ObjectMapper objectMapper;

    public void analyze(Long meetingId) {
        // 1. 회의 조회
        Meeting meeting =
                meetingRepository.findById(meetingId).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회의입니다."));
        Project project = meeting.getProject();
        if (project == null) {
            throw new ServiceException("400-1", "회의에 연결된 프로젝트가 없습니다.");
        }

        // 2. 컨텍스트 구성
        String context = buildContext(meeting);

        // 3. GLM 호출 및 응답 파싱
        AnalysisResult result = callGlm(context);

        // 4. 분석 결과를 통합 컨텍스트 저장 경로로 전달
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
        List<ContextCache> caches = contextCacheRepository.findByMeetingOrderByCreatedAtAsc(meeting);
        List<Utterance> utterances = utteranceRepository.findByMeetingOrderBySpokenAtAsc(meeting);

        StringBuilder sb = new StringBuilder();

        if (!caches.isEmpty()) {
            sb.append("[이전 대화 요약]\n");
            caches.forEach(cache -> sb.append(cache.getCompressedText()).append("\n"));
            sb.append("\n");
        }

        sb.append("[회의 대화 내용]\n");
        utterances.forEach(u -> sb.append(String.format("%s: %s\n", u.getSpeakerName(), u.getContent())));

        return sb.toString();
    }

    private AnalysisResult callGlm(String context) {
        String raw = glmClient.chat(SYSTEM_PROMPT, context);

        // GLM이 마크다운 코드블록으로 감쌀 경우 제거
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
