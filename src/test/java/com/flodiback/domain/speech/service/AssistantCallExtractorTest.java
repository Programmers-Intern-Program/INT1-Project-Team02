package com.flodiback.domain.speech.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssistantCallExtractorTest {

    @Test
    void extractQuestion_returnsQuestionAfterExactWakeWord() {
        assertThat(AssistantCallExtractor.extractQuestion("클로드야, 아까 말랑이 얘기 요약해줘"))
                .isEqualTo("아까 말랑이 얘기 요약해줘");
        assertThat(AssistantCallExtractor.extractQuestion("AI야 인증 방식 뭐였지?")).isEqualTo("인증 방식 뭐였지?");
        assertThat(AssistantCallExtractor.extractQuestion("봇아: 회의 결론 알려줘")).isEqualTo("회의 결론 알려줘");
    }

    @Test
    void extractQuestion_acceptsCommonFlodiSttMisrecognitions() {
        assertThat(AssistantCallExtractor.extractQuestion("플로디아 아까 말랑이 얘기 요약해줘"))
                .isEqualTo("아까 말랑이 얘기 요약해줘");
        assertThat(AssistantCallExtractor.extractQuestion("플로드야 아까 말랑이 얘기 요약해줘"))
                .isEqualTo("아까 말랑이 얘기 요약해줘");
        assertThat(AssistantCallExtractor.extractQuestion("필로디야 아까 말랑이 얘기 요약해줘"))
                .isEqualTo("아까 말랑이 얘기 요약해줘");
    }

    @Test
    void extractQuestion_returnsNullWhenWakeWordHasNoQuestion() {
        assertThat(AssistantCallExtractor.extractQuestion("플로디야!")).isNull();
        assertThat(AssistantCallExtractor.extractQuestion("클로드야")).isNull();
    }

    @Test
    void extractQuestion_avoidsBroadContextOnlyRequestsWithoutAssistantCall() {
        assertThat(AssistantCallExtractor.extractQuestion("아까 말랑이 얘기 요약해줘")).isNull();
        assertThat(AssistantCallExtractor.extractQuestion("플로디가 아까 정리한 내용 말해줘")).isNull();
    }
}
