package com.flodiback.domain.speech.service;

import java.util.List;
import java.util.concurrent.TimeUnit;

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

    private static final int ROLLING_SUMMARY_MAX_CHARS = 1400;
    private static final int UNRESOLVED_ITEMS_MAX_CHARS = 700;
    private static final int PROJECT_METADATA_MAX_CHARS = 600;
    private static final int DECISION_CONTENT_MAX_CHARS = 500;
    private static final int PAST_SUMMARY_MAX_CHARS = 800;
    private static final int WORK_LOG_TASK_MAX_CHARS = 240;

    private static final String SYSTEM_PROMPT = """
            너는 Discord 회의에 참여하는 AI 회의 보조자야.
            항상 회의 컨텍스트를 먼저 확인하고 한국어로 2~3문장만 답해줘.
            회의 컨텍스트에 근거가 있으면 "[회의 기반]"으로 시작해 답해줘.
            회의 컨텍스트에 답이 없거나 질문이 회의와 무관하면 "회의 내용에서는 해당 내용을 찾지 못했습니다."라고 먼저 말하고 이어서 "[일반 지식 기반]"으로 네가 알고 있는 범위에서 간결하게 답해줘.
            실제 웹 검색은 하지 않으므로 "웹에서 찾아보니", "검색 결과에 따르면"처럼 외부 검색을 한 것처럼 말하지 마.
            """;

    private final ContextService contextService;
    private final AiChatService aiChatService;

    public String generateAnswer(Long meetingId, String question) {
        long totalStartedAtNanos = System.nanoTime();
        long contextElapsedMs = -1L;
        long promptElapsedMs = -1L;
        long chatElapsedMs = -1L;
        int promptChars = -1;
        int recentUtteranceCount = -1;
        int questionDecisionCount = -1;
        int questionSummaryCount = -1;
        try {
            long contextStartedAtNanos = System.nanoTime();
            ContextResponse context;
            try {
                context = contextService.assemble(meetingId, question);
            } finally {
                contextElapsedMs = elapsedMillis(contextStartedAtNanos);
            }

            long promptStartedAtNanos = System.nanoTime();
            String userPrompt;
            try {
                userPrompt = buildUserPrompt(context, question);
            } finally {
                promptElapsedMs = elapsedMillis(promptStartedAtNanos);
            }
            promptChars = userPrompt.length();
            recentUtteranceCount = context.shortTerm().recentUtterances().size();
            questionDecisionCount = context.questionContext().decisions().size();
            questionSummaryCount = context.questionContext().pastSummaries().size();

            log.info(
                    "AI answer chat call started. meetingId={}, promptChars={}, systemPromptChars={}, recentUtterances={}, questionDecisions={}, questionSummaries={}",
                    meetingId,
                    promptChars,
                    SYSTEM_PROMPT.length(),
                    recentUtteranceCount,
                    questionDecisionCount,
                    questionSummaryCount);

            long chatStartedAtNanos = System.nanoTime();
            String answer;
            try {
                answer = aiChatService.generateShortAnswer(SYSTEM_PROMPT, userPrompt);
            } finally {
                chatElapsedMs = elapsedMillis(chatStartedAtNanos);
            }
            long totalElapsedMs = elapsedMillis(totalStartedAtNanos);

            log.info(
                    "AI answer generated. meetingId={}, totalMs={}, contextMs={}, promptMs={}, chatMs={}, promptChars={}, recentUtterances={}, questionDecisions={}, questionSummaries={}",
                    meetingId,
                    totalElapsedMs,
                    contextElapsedMs,
                    promptElapsedMs,
                    chatElapsedMs,
                    promptChars,
                    recentUtteranceCount,
                    questionDecisionCount,
                    questionSummaryCount);

            return StringUtils.hasText(answer) ? answer.strip() : null;
        } catch (RuntimeException e) {
            log.warn(
                    "AI answer generation failed. meetingId={}, totalMs={}, contextMs={}, promptMs={}, chatMs={}, promptChars={}, recentUtterances={}, questionDecisions={}, questionSummaries={}, exceptionType={}, reason={}",
                    meetingId,
                    elapsedMillis(totalStartedAtNanos),
                    contextElapsedMs,
                    promptElapsedMs,
                    chatElapsedMs,
                    promptChars,
                    recentUtteranceCount,
                    questionDecisionCount,
                    questionSummaryCount,
                    e.getClass().getSimpleName(),
                    e.getMessage());
            return null;
        }
    }

    public String extractQuestion(String speechText) {
        return AssistantCallExtractor.extractQuestion(speechText);
    }

    private String buildUserPrompt(ContextResponse context, String question) {
        StringBuilder prompt = new StringBuilder();

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
        prompt.append("메타데이터: ")
                .append(valueOrNone(truncate(context.metadata(), PROJECT_METADATA_MAX_CHARS)))
                .append("\n");

        prompt.append("\n최근 결정사항:\n");
        appendDecisions(prompt, context.recentDecisions());

        prompt.append("\n최근 회의 요약:\n");
        appendPastSummaries(prompt, context.recentSummaries());

        prompt.append("\n미해결 사항:\n");
        appendTextBlock(prompt, truncate(context.unresolvedItems(), UNRESOLVED_ITEMS_MAX_CHARS));

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
        if (decisions == null || decisions.isEmpty()) {
            prompt.append("- 없음\n");
            return;
        }

        decisions.forEach(decision -> prompt.append("- ")
                .append(truncate(decision.content(), DECISION_CONTENT_MAX_CHARS))
                .append(" (")
                .append(decision.decidedAt())
                .append(")\n"));
    }

    private void appendPastSummaries(StringBuilder prompt, List<PastSummary> pastSummaries) {
        if (pastSummaries == null || pastSummaries.isEmpty()) {
            prompt.append("- 없음\n");
            return;
        }

        pastSummaries.forEach(summary -> prompt.append("- ")
                .append(truncate(summary.summary(), PAST_SUMMARY_MAX_CHARS))
                .append(" (")
                .append(summary.createdAt())
                .append(")\n"));
    }

    private void appendRollingSummary(StringBuilder prompt, String rollingSummary) {
        if (!StringUtils.hasText(rollingSummary)) {
            prompt.append("- 없음\n");
            return;
        }
        prompt.append(truncate(rollingSummary, ROLLING_SUMMARY_MAX_CHARS).strip())
                .append("\n");
    }

    private void appendRecentUtterances(StringBuilder prompt, List<UtteranceSummary> recentUtterances) {
        if (recentUtterances == null || recentUtterances.isEmpty()) {
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
                .append(truncate(workLog.task(), WORK_LOG_TASK_MAX_CHARS))
                .append(" / 담당자: ")
                .append(valueOrNone(workLog.assigneeName()))
                .append(" / 기한: ")
                .append(workLog.dueDate() == null ? "없음" : workLog.dueDate())
                .append(" / 상태: ")
                .append(valueOrNone(workLog.status()))
                .append("\n"));
    }

    private String valueOrNone(String value) {
        return StringUtils.hasText(value) ? value : "없음";
    }

    private String truncate(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String stripped = value.strip();
        return stripped.length() <= maxChars ? stripped : stripped.substring(0, maxChars) + "...";
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
