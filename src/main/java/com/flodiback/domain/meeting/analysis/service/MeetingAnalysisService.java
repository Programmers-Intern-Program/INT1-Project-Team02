package com.flodiback.domain.meeting.analysis.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.domain.meeting.analysis.dto.AnalysisResult;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.MeetingSummaryRepository;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.global.client.GlmClient;

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
                { "assigneeName": "담당자 이름", "task": "작업 내용", "dueDate": "YYYY-MM-DD 또는 null" }
              ],
              "decisions": [
                { "content": "결정 내용" }
              ]
            }
            """;

    private final MeetingRepository meetingRepository;
    private final ContextCacheRepository contextCacheRepository;
    private final UtteranceRepository utteranceRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final GlmClient glmClient;
    private final ObjectMapper objectMapper;

    public void analyze(Long meetingId) {
        // 1. 회의 조회
        Meeting meeting =
                meetingRepository.findById(meetingId).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회의입니다."));

        // 2. 컨텍스트 구성
        String context = buildContext(meeting);

        // 3. GLM 호출 및 응답 파싱
        AnalysisResult result = callGlm(context);

        // 4. MeetingSummary 저장
        MeetingSummary summary = MeetingSummary.builder()
                .meeting(meeting)
                .summary(result.summary())
                .unresolvedItems(result.unresolvedItems())
                .build();
        meetingSummaryRepository.save(summary);

        // 5. WorkLog 저장 (스켈레톤)
        // TODO: WorkLogService 구현 후 연동
        // result.worklogs().forEach(item -> ...);

        // 6. Decision 저장 (스켈레톤)
        // TODO: DecisionService 구현 후 연동 (embedding 포함)
        // result.decisions().forEach(item -> ...);
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
