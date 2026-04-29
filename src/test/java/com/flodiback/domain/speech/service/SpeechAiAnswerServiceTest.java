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
import com.flodiback.domain.meeting.meetinglog.dto.ContextResponse;
import com.flodiback.domain.meeting.meetinglog.dto.DecisionSummary;
import com.flodiback.domain.meeting.meetinglog.dto.LongTermContext;
import com.flodiback.domain.meeting.meetinglog.dto.PastSummary;
import com.flodiback.domain.meeting.meetinglog.dto.ShortTermContext;
import com.flodiback.domain.meeting.meetinglog.dto.UtteranceSummary;
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
    void generateAnswerIfCalled_returnsNull_whenWakeWordDoesNotExist() {
        String result = speechAiAnswerService.generateAnswerIfCalled(1L, "이번 스프린트 목표를 정해봅시다.");

        assertThat(result).isNull();
        verify(contextService, never()).assemble(1L, "이번 스프린트 목표를 정해봅시다.");
        verify(aiChatService, never()).generateAnswer(anyString(), anyString());
    }

    @Test
    void generateAnswerIfCalled_usesContextAndAiChat_whenWakeWordExists() {
        ContextResponse context = new ContextResponse(
                new ShortTermContext(List.of(new UtteranceSummary("김철수", "인증은 JWT로 하죠.", null))),
                new LongTermContext(
                        "Flodi",
                        "Spring Boot",
                        null,
                        List.of(new DecisionSummary("인증 방식은 JWT로 한다.", null)),
                        List.of(new PastSummary("로그인 기능 담당자를 정했다.", null))));

        given(contextService.assemble(1L, "인증 방식 뭐로 하기로 했지?")).willReturn(context);
        given(aiChatService.generateAnswer(anyString(), anyString())).willReturn("인증 방식은 JWT로 결정했습니다.");

        String result = speechAiAnswerService.generateAnswerIfCalled(1L, "AI야, 인증 방식 뭐로 하기로 했지?");

        assertThat(result).isEqualTo("인증 방식은 JWT로 결정했습니다.");
        verify(contextService).assemble(1L, "인증 방식 뭐로 하기로 했지?");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateAnswer(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue())
                .contains("Flodi")
                .contains("인증 방식은 JWT로 한다.")
                .contains("로그인 기능 담당자를 정했다.")
                .contains("[질문]")
                .contains("인증 방식 뭐로 하기로 했지?");
    }

    @Test
    void generateAnswerIfCalled_returnsNull_whenQuestionIsBlank() {
        String result = speechAiAnswerService.generateAnswerIfCalled(1L, "봇아!");

        assertThat(result).isNull();
        verify(contextService, never()).assemble(1L, "");
        verify(aiChatService, never()).generateAnswer(anyString(), anyString());
    }

    @Test
    void generateAnswerIfCalled_returnsNull_whenAiChatFails() {
        ContextResponse context = ContextResponse.noProject(List.of());
        given(contextService.assemble(1L, "토큰 만료 시간 정했어?")).willReturn(context);
        given(aiChatService.generateAnswer(anyString(), anyString())).willThrow(new RuntimeException("GLM error"));

        String result = speechAiAnswerService.generateAnswerIfCalled(1L, "클로드야 토큰 만료 시간 정했어?");

        assertThat(result).isNull();
        verify(contextService).assemble(1L, "토큰 만료 시간 정했어?");
        verify(aiChatService).generateAnswer(anyString(), anyString());
    }
}
