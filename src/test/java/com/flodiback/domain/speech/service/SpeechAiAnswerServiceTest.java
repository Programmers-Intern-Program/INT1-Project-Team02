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
        String result = speechAiAnswerService.extractQuestion("이번 스프린트 목표를 정해봅시다");

        assertThat(result).isNull();
    }

    @Test
    void extractQuestion_returnsQuestionAfterWakeWord() {
        String result = speechAiAnswerService.extractQuestion("플로디야, 인증 방식 뭐로 정했어?");

        assertThat(result).isEqualTo("인증 방식 뭐로 정했어?");
    }

    @Test
    void extractQuestion_doesNotCallDependencies_whenWakeWordDoesNotExist() {
        String result = speechAiAnswerService.extractQuestion("이번 스프린트 목표를 정해봅시다");

        assertThat(result).isNull();
        verify(contextService, never()).assemble(1L, "이번 스프린트 목표를 정해봅시다");
        verify(aiChatService, never()).generateShortAnswer(anyString(), anyString());
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
                        List.of(new DecisionSummary(1L, "auth uses JWT", null)),
                        List.of(new PastSummary(1L, "assigned login feature", null)),
                        "API response format undecided",
                        List.of(new WorkLogSummary(1L, "Alice", "write login API", null, "TODO"))),
                new ShortTermContext(null, List.of(new UtteranceSummary("Alice", "Let's use JWT.", null))),
                QuestionContext.empty());

        given(contextService.assemble(1L, "what auth did we choose?")).willReturn(context);
        given(aiChatService.generateShortAnswer(anyString(), anyString())).willReturn("[meeting] auth uses JWT.");

        String result = speechAiAnswerService.generateAnswer(1L, "what auth did we choose?");

        assertThat(result).isEqualTo("[meeting] auth uses JWT.");
        verify(contextService).assemble(1L, "what auth did we choose?");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateShortAnswer(systemPromptCaptor.capture(), userPromptCaptor.capture());
        assertThat(systemPromptCaptor.getValue()).contains("2~3");
        assertThat(userPromptCaptor.getValue())
                .contains("Flodi")
                .contains("auth uses JWT")
                .contains("assigned login feature")
                .contains("API response format undecided")
                .contains("write login API")
                .contains("what auth did we choose?");
    }

    @Test
    void generateAnswer_truncatesLongContextText() {
        String longMetadata = "m".repeat(650);
        String longDecision = "d".repeat(550);
        String longSummary = "s".repeat(850);
        ContextResponse context = new ContextResponse(
                new MeetingStartContext(
                        1L,
                        10L,
                        "Flodi",
                        "Spring Boot",
                        longMetadata,
                        List.of(new DecisionSummary(1L, longDecision, null)),
                        List.of(new PastSummary(1L, longSummary, null)),
                        "u".repeat(750),
                        List.of(new WorkLogSummary(1L, "Alice", "w".repeat(260), null, "TODO"))),
                new ShortTermContext("r".repeat(1500), List.of()),
                QuestionContext.empty());
        given(contextService.assemble(1L, "summarize")).willReturn(context);
        given(aiChatService.generateShortAnswer(anyString(), anyString())).willReturn("done");

        speechAiAnswerService.generateAnswer(1L, "summarize");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateShortAnswer(anyString(), userPromptCaptor.capture());
        String prompt = userPromptCaptor.getValue();
        assertThat(prompt).contains("m".repeat(600) + "...");
        assertThat(prompt).contains("d".repeat(500) + "...");
        assertThat(prompt).contains("s".repeat(800) + "...");
        assertThat(prompt).contains("u".repeat(700) + "...");
        assertThat(prompt).contains("w".repeat(240) + "...");
        assertThat(prompt).contains("r".repeat(1400) + "...");
    }

    @Test
    void extractQuestion_acceptsMisrecognizedFlodiWakeWord() {
        String result = speechAiAnswerService.extractQuestion("플로디아 아까 말한 내용 요약해줘");

        assertThat(result).isEqualTo("아까 말한 내용 요약해줘");
        verify(contextService, never()).assemble(1L, "아까 말한 내용 요약해줘");
        verify(aiChatService, never()).generateShortAnswer(anyString(), anyString());
    }

    @Test
    void extractQuestion_returnsNull_whenQuestionIsBlank() {
        String result = speechAiAnswerService.extractQuestion("플로디야!");

        assertThat(result).isNull();
        verify(contextService, never()).assemble(1L, "");
        verify(aiChatService, never()).generateShortAnswer(anyString(), anyString());
    }

    @Test
    void generateAnswer_returnsNull_whenAiChatFails() {
        ContextResponse context = ContextResponse.noProject(null, List.of());
        given(contextService.assemble(1L, "token expiry?")).willReturn(context);
        given(aiChatService.generateShortAnswer(anyString(), anyString())).willThrow(new RuntimeException("AI error"));

        String result = speechAiAnswerService.generateAnswer(1L, "token expiry?");

        assertThat(result).isNull();
        verify(contextService).assemble(1L, "token expiry?");
        verify(aiChatService).generateShortAnswer(anyString(), anyString());
    }
}
