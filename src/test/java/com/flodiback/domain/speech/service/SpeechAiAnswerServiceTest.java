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
                new ShortTermContext(null, List.of(new UtteranceSummary("김철수", "인증은 JWT로 하죠.", null))),
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

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateAnswer(systemPromptCaptor.capture(), userPromptCaptor.capture());
        assertThat(systemPromptCaptor.getValue())
                .contains("[회의 기반]")
                .contains("[일반 지식 기반]")
                .contains("실제 웹 검색은 하지 않으므로");
        assertThat(userPromptCaptor.getValue())
                .contains("[답변 규칙]")
                .contains("[회의 기반]")
                .contains("[일반 지식 기반]")
                .contains("Flodi")
                .contains("인증 방식은 JWT로 한다.")
                .contains("로그인 기능 담당자를 정했다.")
                .contains("[질문]")
                .contains("인증 방식 뭐로 하기로 했지?");
    }

    @Test
    void generateAnswerIfCalled_allowsGeneralKnowledgeFallback_whenContextDoesNotContainAnswer() {
        ContextResponse context = ContextResponse.noProject(null, List.of());
        given(contextService.assemble(1L, "코끼리 나이는 몇 살이야?")).willReturn(context);
        given(aiChatService.generateAnswer(anyString(), anyString()))
                .willReturn("회의 내용에서는 해당 내용을 찾지 못했습니다. [일반 지식 기반] 코끼리는 보통 60~70년 정도 살 수 있습니다.");

        String result = speechAiAnswerService.generateAnswerIfCalled(1L, "클로드야, 코끼리 나이는 몇 살이야?");

        assertThat(result).isEqualTo("회의 내용에서는 해당 내용을 찾지 못했습니다. [일반 지식 기반] 코끼리는 보통 60~70년 정도 살 수 있습니다.");
        verify(contextService).assemble(1L, "코끼리 나이는 몇 살이야?");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateAnswer(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue())
                .contains("회의 컨텍스트에 답이 없거나 무관하면")
                .contains("회의 내용에 없다고 밝힌 뒤")
                .contains("[일반 지식 기반]")
                .contains("실제 웹 검색은 하지 않았으므로")
                .contains("코끼리 나이는 몇 살이야?");
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
        ContextResponse context = ContextResponse.noProject(null, List.of());
        given(contextService.assemble(1L, "토큰 만료 시간 정했어?")).willReturn(context);
        given(aiChatService.generateAnswer(anyString(), anyString())).willThrow(new RuntimeException("GLM error"));

        String result = speechAiAnswerService.generateAnswerIfCalled(1L, "클로드야 토큰 만료 시간 정했어?");

        assertThat(result).isNull();
        verify(contextService).assemble(1L, "토큰 만료 시간 정했어?");
        verify(aiChatService).generateAnswer(anyString(), anyString());
    }
}
