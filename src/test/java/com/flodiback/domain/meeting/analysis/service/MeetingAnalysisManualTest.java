package com.flodiback.domain.meeting.analysis.service;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.domain.meeting.analysis.dto.AnalysisResult;
import com.flodiback.global.client.GlmClient;

/**
 * MeetingAnalysisService 프롬프트 수동 검증 테스트.
 *
 * <p>실제 Z.AI API를 호출하므로 토큰이 소모됩니다.
 * 자동 빌드·CI에서는 실행되지 않으며, 검증이 필요할 때만 @Disabled를 제거하고 실행하세요.
 *
 * <p>실행 전 로컬 환경변수에 GLM_ENABLED=true, GLM_API_KEY가 설정되어 있어야 합니다.
 *
 * <p>샘플 회의록 추가 시 {@code SAMPLES} 맵에 한 줄만 추가하면 됩니다.
 */
@Disabled("수동 실행 전용 — 토큰 소모 주의. 실행 시 @Disabled 제거")
@SpringBootTest
class MeetingAnalysisManualTest {

    @Autowired
    private GlmClient glmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // 샘플 추가 시 이 맵에 한 줄 추가
    private static final Map<String, String> SAMPLES = Map.of("스프린트 회의", """
                    [회의 대화 내용]
                    김민준: 이번 스프린트에서 로그인 API 구현이 지연되고 있어요. 이번 주까지 마무리 가능할까요?
                    이서연: 제가 담당인데, 소셜 로그인 OAuth 연동 부분이 생각보다 복잡해서요. 다음 주 화요일까지는 완료할 수 있을 것 같습니다.
                    박준호: 그러면 프론트 연동 일정도 같이 밀려야 하는데, 저는 다음 주 수요일부터 시작하겠습니다.
                    김민준: 알겠습니다. 그리고 알림 기능은 이번 스프린트 스코프에서 제외하기로 하죠. 다음 스프린트로 넘기겠습니다.
                    이서연: 동의합니다. 알림은 백엔드 설계가 더 필요한 상황이에요.
                    박준호: DB 인덱스 최적화 작업은 제가 이번 주 금요일까지 끝낼게요.
                    김민준: 좋아요. 오늘 회의는 여기서 마무리하겠습니다.
                    """);

    static Stream<Arguments> sampleProvider() {
        return SAMPLES.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("sampleProvider")
    void 회의록_분석(String label, String transcript) throws Exception {
        String raw = glmClient.chat(SYSTEM_PROMPT, transcript);

        // 서비스 로직과 동일한 마크다운 코드블록 제거
        String json = raw.strip()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "");

        AnalysisResult result = objectMapper.readValue(json, AnalysisResult.class);

        System.out.println("\n=== [" + label + "] GLM 원본 응답 ===");
        System.out.println(raw);
        System.out.println("\n=== [" + label + "] 파싱 결과 ===");
        System.out.println("summary        : " + result.summary());
        System.out.println("unresolvedItems: " + result.unresolvedItems());
        System.out.println("worklogs       : " + result.worklogs());
        System.out.println("decisions      : " + result.decisions());
    }
}
