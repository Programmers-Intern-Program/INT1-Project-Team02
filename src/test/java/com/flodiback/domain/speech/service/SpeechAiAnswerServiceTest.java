package com.flodiback.domain.speech.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flodiback.domain.ai.service.AiChatService;
import com.flodiback.domain.meeting.meeting.context.MeetingStartContext;
import com.flodiback.domain.meeting.meetinglog.dto.ContextResponse;
import com.flodiback.domain.meeting.meetinglog.dto.DecisionSummary;
import com.flodiback.domain.meeting.meetinglog.dto.PastSummary;
import com.flodiback.domain.meeting.meetinglog.dto.QuestionContext;
import com.flodiback.domain.meeting.meetinglog.dto.ShortTermContext;
import com.flodiback.domain.meeting.meetinglog.dto.UtteranceSummary;
import com.flodiback.domain.meeting.meetinglog.dto.WorkLogSummary;
import com.flodiback.domain.meeting.meetinglog.service.ContextService;

@ExtendWith(MockitoExtension.class)
class SpeechAiAnswerServiceTest {

    @Mock
    private ContextService contextService;

    @Mock
    private AiChatService aiChatService;

    @InjectMocks
    private SpeechAiAnswerService speechAiAnswerService;

    @Test
    void extractQuestion_returnsNull_whenWakeWordDoesNotExist() {
        String result = speechAiAnswerService.extractQuestion("이번 스프린트 목표를 정해봅시다.");

        assertThat(result).isNull();
    }

    @Test
    void extractQuestion_returnsQuestionAfterWakeWord() {
        String result = speechAiAnswerService.extractQuestion("플로디야, 인증 방식 뭐로 정했어?");

        assertThat(result).isEqualTo("인증 방식 뭐로 정했어?");
    }

    @Test
    void extractQuestion_doesNotCallDependencies_whenWakeWordDoesNotExist() {
        String result = speechAiAnswerService.extractQuestion("이번 스프린트 목표를 정해봅시다.");

        assertThat(result).isNull();
        verify(contextService, never()).assemble(1L, "이번 스프린트 목표를 정해봅시다.");
        verify(aiChatService, never()).generateAnswer(anyString(), anyString());
    }

    @Test
    void generateAnswer_usesContextAndAiChat() {
        ContextResponse context = new ContextResponse(
                new MeetingStartContext(
                        1L,
                        10L,
                        "Flodi",
                        "Spring Boot",
                        "metadata",
                        List.of(new DecisionSummary(1L, "인증 방식은 JWT로 한다.", null)),
                        List.of(new PastSummary(1L, "로그인 기능 담당자를 정했다.", null)),
                        "API 응답 형식 미정",
                        List.of(new WorkLogSummary(1L, "김철수", "로그인 API 작성", null, "TODO"))),
                new ShortTermContext(null, List.of(new UtteranceSummary("김철수", "인증은 JWT로 하자.", null))),
                QuestionContext.empty());

        given(contextService.assemble(1L, "인증 방식 뭐로 하기로 했어?")).willReturn(context);
        given(aiChatService.generateAnswer(anyString(), anyString())).willReturn("[회의 기반] 인증 방식은 JWT로 결정했습니다.");

        String result = speechAiAnswerService.generateAnswer(1L, "인증 방식 뭐로 하기로 했어?");

        assertThat(result).isEqualTo("[회의 기반] 인증 방식은 JWT로 결정했습니다.");
        verify(contextService).assemble(1L, "인증 방식 뭐로 하기로 했어?");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateAnswer(systemPromptCaptor.capture(), userPromptCaptor.capture());
        assertThat(systemPromptCaptor.getValue())
                .contains("[회의 기반]")
                .contains("[일반 지식 기반]")
                .contains("2~3문장");
        assertThat(userPromptCaptor.getValue())
                .contains("[답변 규칙]")
                .contains("[회의 시작 컨텍스트]")
                .contains("[현재 회의 컨텍스트]")
                .contains("[질문 관련 추가 기억]")
                .contains("[질문]")
                .contains("Flodi")
                .contains("인증 방식은 JWT로 한다.")
                .contains("로그인 기능 담당자를 정했다.")
                .contains("API 응답 형식 미정")
                .contains("로그인 API 작성")
                .contains("인증 방식 뭐로 하기로 했어?");
    }

    @Test
    void generateAnswer_truncatesLongContextText() {
        String longMetadata = "m".repeat(350);
        String longDecision = "d".repeat(300);
        String longSummary = "s".repeat(450);
        ContextResponse context = new ContextResponse(
                new MeetingStartContext(
                        1L,
                        10L,
                        "Flodi",
                        "Spring Boot",
                        longMetadata,
                        List.of(new DecisionSummary(1L, longDecision, null)),
                        List.of(new PastSummary(1L, longSummary, null)),
                        "u".repeat(450),
                        List.of(new WorkLogSummary(1L, "김철수", "w".repeat(200), null, "TODO"))),
                new ShortTermContext("r".repeat(900), List.of()),
                QuestionContext.empty());
        given(contextService.assemble(1L, "요약해줘")).willReturn(context);
        given(aiChatService.generateAnswer(anyString(), anyString())).willReturn("done");

        speechAiAnswerService.generateAnswer(1L, "요약해줘");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateAnswer(anyString(), userPromptCaptor.capture());
        String prompt = userPromptCaptor.getValue();
        assertThat(prompt).contains("m".repeat(300) + "...");
        assertThat(prompt).contains("d".repeat(250) + "...");
        assertThat(prompt).contains("s".repeat(400) + "...");
        assertThat(prompt).contains("u".repeat(400) + "...");
        assertThat(prompt).contains("w".repeat(160) + "...");
        assertThat(prompt).contains("r".repeat(800) + "...");
    }

    @Test
    void extractQuestion_acceptsMisrecognizedFlodiWakeWord() {
        String result = speechAiAnswerService.extractQuestion("플로디아 아까 말랑이 관련 이야기 요약 좀 해줄래?");

        assertThat(result).isEqualTo("아까 말랑이 관련 이야기 요약 좀 해줄래?");
        verify(contextService, never()).assemble(1L, "아까 말랑이 관련 이야기 요약 좀 해줄래?");
        verify(aiChatService, never()).generateAnswer(anyString(), anyString());
    }

    @Test
    void extractQuestion_returnsNull_whenQuestionIsBlank() {
        String result = speechAiAnswerService.extractQuestion("플로디야!");

        assertThat(result).isNull();
        verify(contextService, never()).assemble(1L, "");
        verify(aiChatService, never()).generateAnswer(anyString(), anyString());
    }

    @Test
    void generateAnswer_returnsNull_whenAiChatFails() {
        ContextResponse context = ContextResponse.noProject(null, List.of());
        given(contextService.assemble(1L, "토큰 만료 시간 정했어?")).willReturn(context);
        given(aiChatService.generateAnswer(anyString(), anyString())).willThrow(new RuntimeException("GLM error"));

        String result = speechAiAnswerService.generateAnswer(1L, "토큰 만료 시간 정했어?");

        assertThat(result).isNull();
        verify(contextService).assemble(1L, "토큰 만료 시간 정했어?");
        verify(aiChatService).generateAnswer(anyString(), anyString());
    }
}
