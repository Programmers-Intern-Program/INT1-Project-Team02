package com.flodiback.domain.speech.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.flodiback.domain.ai.service.AiChatService;
import com.flodiback.domain.meeting.meeting.context.MeetingStartContext;
import com.flodiback.domain.meeting.meetinglog.dto.ContextResponse;
import com.flodiback.domain.meeting.meetinglog.dto.DecisionSummary;
import com.flodiback.domain.meeting.meetinglog.dto.PastSummary;
import com.flodiback.domain.meeting.meetinglog.dto.QuestionContext;
import com.flodiback.domain.meeting.meetinglog.dto.UtteranceSummary;
import com.flodiback.domain.meeting.meetinglog.dto.WorkLogSummary;
import com.flodiback.domain.meeting.meetinglog.service.ContextService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechAiAnswerService {

    private static final List<String> WAKE_WORDS = List.of("AI야", "ai야", "봇아", "클로드야", "플로디야", "flodiya", "plodiya");
    private static final Pattern LEADING_PUNCTUATION = Pattern.compile("^[\\s,，.。?？!！:：;；-]+");
    private static final String SYSTEM_PROMPT = """
            너는 Discord 회의에 참여하는 AI 회의 보조자야.
            항상 회의 컨텍스트를 먼저 확인하고 한국어로 2~3문장만 답해줘.
            회의 컨텍스트에 근거가 있으면 "[회의 기반]"으로 시작해 답해줘.
            회의 컨텍스트에 답이 없거나 질문이 회의와 무관하면 "회의 내용에서는 해당 내용을 찾지 못했습니다."라고 먼저 말하고, 이어서 "[일반 지식 기반]"으로 네가 알고 있는 범위에서 간결하게 답해줘.
            실제 웹 검색은 하지 않으므로 "웹에서 찾았다", "검색 결과에 따르면"처럼 외부 검색을 한 것처럼 말하지 마.
            """;

    private final ContextService contextService;
    private final AiChatService aiChatService;

    public String generateAnswerIfCalled(Long meetingId, String speechText) {
        String question = extractQuestion(speechText);
        if (!StringUtils.hasText(question)) {
            return null;
        }

        try {
            ContextResponse context = contextService.assemble(meetingId, question);
            String answer = aiChatService.generateAnswer(SYSTEM_PROMPT, buildUserPrompt(context, question));
            return StringUtils.hasText(answer) ? answer.strip() : null;
        } catch (RuntimeException e) {
            // STT 원문 저장이 더 중요하므로 AI 답변 실패는 응답만 비우고 회의록 흐름은 유지합니다.
            log.warn("AI 답변 생성에 실패했습니다. meetingId={}, reason={}", meetingId, e.getMessage());
            return null;
        }
    }

    private String extractQuestion(String speechText) {
        // 빈 STT 문장은 AI가 답할 질문으로 볼 수 없으므로 바로 종료합니다.
        if (!StringUtils.hasText(speechText)) {
            return null;
        }

        int wakeWordIndex = Integer.MAX_VALUE;
        String matchedWakeWord = null;

        // 한 문장에 호출어가 여러 개 있어도 가장 앞에 나온 호출어를 기준으로 질문을 자릅니다.
        for (String wakeWord : WAKE_WORDS) {
            int index = speechText.indexOf(wakeWord);
            if (index >= 0 && index < wakeWordIndex) {
                wakeWordIndex = index;
                matchedWakeWord = wakeWord;
            }
        }

        if (matchedWakeWord == null) {
            return null;
        }

        // 호출어 뒤에 붙은 쉼표, 물음표, 공백을 제거해 실제 질문만 남깁니다.
        String rawQuestion = speechText.substring(wakeWordIndex + matchedWakeWord.length());
        String question =
                LEADING_PUNCTUATION.matcher(rawQuestion).replaceFirst("").strip();

        return StringUtils.hasText(question) ? question : null;
    }

    private String buildUserPrompt(ContextResponse context, String question) {
        // GLM이 회의 맥락을 함께 읽을 수 있도록 컨텍스트를 섹션별 텍스트로 정리합니다.
        StringBuilder prompt = new StringBuilder();

        // 회의 맥락을 우선하되, 근거가 없으면 일반 지식 답변임을 표시하게 합니다.
        prompt.append("[답변 규칙]\n");
        prompt.append("- 회의 컨텍스트에 답이 있으면 [회의 기반]으로 답변\n");
        prompt.append("- 회의 컨텍스트에 답이 없거나 무관하면 회의 내용에 없다고 밝힌 뒤 [일반 지식 기반]으로 답변\n");
        prompt.append("- 실제 웹 검색은 하지 않았으므로 웹 검색 출처가 있는 것처럼 표현하지 않음\n\n");

        prompt.append("[회의 시작 컨텍스트]\n");
        appendMeetingStartContext(prompt, context.startContext());

        prompt.append("\n[현재 회의 컨텍스트]\n");
        prompt.append("롤링 요약:\n");
        appendRollingSummary(prompt, context.shortTerm().rollingSummary());

        prompt.append("\n최근 발화:\n");
        appendRecentUtterances(prompt, context.shortTerm().recentUtterances());

        prompt.append("\n[질문 관련 추가 기억]\n");
        appendQuestionContext(prompt, context.questionContext());

        prompt.append("\n[질문]\n").append(question);

        return prompt.toString();
    }

    private void appendMeetingStartContext(StringBuilder prompt, MeetingStartContext context) {
        prompt.append("프로젝트명: ").append(valueOrNone(context.projectName())).append("\n");
        prompt.append("기술 스택: ").append(valueOrNone(context.techStack())).append("\n");
        prompt.append("메타데이터: ").append(valueOrNone(context.metadata())).append("\n");

        prompt.append("\n최근 결정사항:\n");
        appendDecisions(prompt, context.recentDecisions());

        prompt.append("\n최근 회의 요약:\n");
        appendPastSummaries(prompt, context.recentSummaries());

        prompt.append("\n미결 사항:\n");
        appendTextBlock(prompt, context.unresolvedItems());

        prompt.append("\n진행 중 작업:\n");
        appendWorkLogs(prompt, context.activeWorkLogs());
    }

    private void appendQuestionContext(StringBuilder prompt, QuestionContext context) {
        prompt.append("관련 결정사항:\n");
        appendDecisions(prompt, context.decisions());

        prompt.append("\n관련 회의 요약:\n");
        appendPastSummaries(prompt, context.pastSummaries());
    }

    private void appendDecisions(StringBuilder prompt, List<DecisionSummary> decisions) {
        // 기존 결정사항은 프로젝트의 장기 기억으로, 회의 중 재확인 질문에 쓰입니다.
        if (decisions == null || decisions.isEmpty()) {
            // 섹션을 비워두지 않고 "없음"을 넣어 GLM이 누락으로 오해하지 않게 합니다.
            prompt.append("- 없음\n");
            return;
        }

        decisions.forEach(decision -> prompt.append("- ")
                .append(decision.content())
                .append(" (")
                .append(decision.decidedAt())
                .append(")\n"));
    }

    private void appendPastSummaries(StringBuilder prompt, List<PastSummary> pastSummaries) {
        // 과거 회의 요약은 현재 회의 이전에 정해진 맥락을 보충합니다.
        if (pastSummaries == null || pastSummaries.isEmpty()) {
            // 과거 요약이 없는 경우에도 프롬프트 구조를 일정하게 유지합니다.
            prompt.append("- 없음\n");
            return;
        }

        pastSummaries.forEach(summary -> prompt.append("- ")
                .append(summary.summary())
                .append(" (")
                .append(summary.createdAt())
                .append(")\n"));
    }

    private void appendRollingSummary(StringBuilder prompt, String rollingSummary) {
        if (!StringUtils.hasText(rollingSummary)) {
            prompt.append("- 없음\n");
            return;
        }
        prompt.append(rollingSummary.strip()).append("\n");
    }

    private void appendRecentUtterances(StringBuilder prompt, List<UtteranceSummary> recentUtterances) {
        // 최근 발화는 방금 진행 중인 회의의 단기 맥락으로 사용합니다.
        if (recentUtterances == null || recentUtterances.isEmpty()) {
            // 최근 대화가 없어도 섹션 의미가 드러나도록 "없음"을 명시합니다.
            prompt.append("- 없음\n");
            return;
        }

        recentUtterances.forEach(utterance -> prompt.append("- [")
                .append(utterance.speakerName())
                .append("] ")
                .append(utterance.content())
                .append("\n"));
    }

    private void appendTextBlock(StringBuilder prompt, String text) {
        if (!StringUtils.hasText(text)) {
            prompt.append("- 없음\n");
            return;
        }
        prompt.append(text.strip()).append("\n");
    }

    private void appendWorkLogs(StringBuilder prompt, List<WorkLogSummary> workLogs) {
        if (workLogs == null || workLogs.isEmpty()) {
            prompt.append("- 없음\n");
            return;
        }

        workLogs.forEach(workLog -> prompt.append("- ")
                .append(workLog.task())
                .append(" / 담당자: ")
                .append(valueOrNone(workLog.assigneeName()))
                .append(" / 기한: ")
                .append(workLog.dueDate() == null ? "없음" : workLog.dueDate())
                .append(" / 상태: ")
                .append(valueOrNone(workLog.status()))
                .append("\n"));
    }

    private String valueOrNone(String value) {
        // 값이 비어 있으면 프롬프트에 null 대신 "없음"을 넣어 읽기 쉽게 만듭니다.
        return StringUtils.hasText(value) ? value : "없음";
    }
}
