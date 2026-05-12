package com.flodiback.bot.stt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BotSttListenerTest {

    private final BotSttListener listener = new BotSttListener(1L, "123456789", "김철수");

    @Test
    void extractAiAnswer_returnsAnswer_whenResponseContainsAiAnswer() throws Exception {
        String responseBody = """
                {
                  "resultCode": "200-1",
                  "msg": "발화가 저장되었습니다.",
                  "data": {
                    "utterance_id": 11,
                    "meeting_id": 1,
                    "ai_answer": "  인증 방식은 JWT로 결정했습니다.  "
                  }
                }
                """;

        String aiAnswer = listener.extractAiAnswer(responseBody);

        assertThat(aiAnswer).isEqualTo("인증 방식은 JWT로 결정했습니다.");
    }

    @Test
    void extractAiAnswer_returnsNull_whenAiAnswerIsNullOrBlank() throws Exception {
        String nullAnswerResponse = """
                {"data":{"ai_answer":null}}
                """;
        String blankAnswerResponse = """
                {"data":{"ai_answer":"   "}}
                """;

        assertThat(listener.extractAiAnswer(nullAnswerResponse)).isNull();
        assertThat(listener.extractAiAnswer(blankAnswerResponse)).isNull();
    }

    @Test
    void splitAiAnswerForDiscord_keepsEveryChunkWithinDiscordLimit() {
        String aiAnswer = "a".repeat(4_500);

        assertThat(listener.splitAiAnswerForDiscord(aiAnswer)).hasSize(3).allSatisfy(chunk -> assertThat(chunk)
                .hasSizeLessThanOrEqualTo(2_000));
        assertThat(listener.splitAiAnswerForDiscord(aiAnswer).get(0)).startsWith("**Flodi 답변**\n");
    }
}
