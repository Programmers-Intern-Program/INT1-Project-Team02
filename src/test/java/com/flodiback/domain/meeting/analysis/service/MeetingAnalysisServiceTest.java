package com.flodiback.domain.meeting.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.domain.meeting.analysis.dto.AnalysisResult;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.UpdateContextRequest;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.meeting.meetinglog.service.ContextService;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.global.client.GlmClient;
import com.flodiback.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class MeetingAnalysisServiceTest {

    @InjectMocks
    private MeetingAnalysisService service;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private ContextCacheRepository contextCacheRepository;

    @Mock
    private UtteranceRepository utteranceRepository;

    @Mock
    private ContextService contextService;

    @Mock
    private GlmClient glmClient;

    @Mock(name = "objectMapper")
    private ObjectMapper objectMapper;

    private final ObjectMapper realMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void analyze_회의없으면_예외발생() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 회의입니다.");
    }

    @Test
    void analyze_캐시없고_발화있을때_updateContext_호출() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("테스트 회의").build();
        Utterance u1 = utterance(meeting, 1L, "장만월", "API 명세 확정했습니다.", 1);
        Utterance u2 = utterance(meeting, 2L, "김철수", "다음 주 금요일까지 구현 완료 예정입니다.", 2);
        String glmResponse = """
                {
                  "summary": "API 명세 확정 및 구현 일정 논의",
                  "unresolvedItems": null,
                  "worklogs": [
                    { "assigneeName": "김철수", "task": "구현 완료", "dueDate": "2026-05-04" },
                    { "assigneeName": "이영희", "task": "테스트 작성", "dueDate": null }
                  ],
                  "decisions": [
                    { "content": "API 명세를 확정한다" }
                  ]
                }
                """;

        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(List.of(u1, u2));
        given(glmClient.chat(anyString(), anyString())).willReturn(glmResponse);
        given(objectMapper.readValue(anyString(), any(Class.class)))
                .willAnswer(inv -> realMapper.readValue((String) inv.getArgument(0), AnalysisResult.class));

        service.analyze(1L);

        ArgumentCaptor<UpdateContextRequest> captor = ArgumentCaptor.forClass(UpdateContextRequest.class);
        verify(contextService).updateContext(org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        UpdateContextRequest saved = captor.getValue();
        assertThat(saved.summary()).isEqualTo("API 명세 확정 및 구현 일정 논의");
        assertThat(saved.unresolvedItems()).isNull();
        assertThat(saved.decisions()).containsExactly("API 명세를 확정한다");
        assertThat(saved.actionItems()).hasSize(2);
        assertThat(saved.actionItems().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 5, 4));
        assertThat(saved.actionItems().get(1).dueDate()).isNull();
    }

    @Test
    void analyze_캐시있을때_latestCache하나와_이후발화만_context에_포함() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("테스트 회의").build();
        ContextCache oldCache = ContextCache.builder()
                .meeting(meeting)
                .version(1)
                .compressedText("old summary")
                .tokenCount(100)
                .compressedUntilUtteranceId(3L)
                .build();
        ContextCache latestCache = ContextCache.builder()
                .meeting(meeting)
                .version(2)
                .compressedText("latest summary")
                .tokenCount(100)
                .compressedUntilUtteranceId(10L)
                .build();
        Utterance later = utterance(meeting, 11L, "장만월", "이후 발화", 2);

        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(latestCache));
        given(utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdAsc(meeting, 10L))
                .willReturn(List.of(later));
        given(glmClient.chat(anyString(), anyString())).willReturn(emptyAnalysisJson());
        given(objectMapper.readValue(anyString(), any(Class.class)))
                .willAnswer(inv -> realMapper.readValue((String) inv.getArgument(0), AnalysisResult.class));

        service.analyze(1L);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(glmClient).chat(anyString(), userPromptCaptor.capture());
        String userPrompt = userPromptCaptor.getValue();
        assertThat(userPrompt).contains("[현재 회의 rolling summary]");
        assertThat(userPrompt).contains("latest summary");
        assertThat(userPrompt).doesNotContain("old summary");
        assertThat(userPrompt).contains("[rolling summary에 아직 반영되지 않은 발화]");
        assertThat(userPrompt).contains("이후 발화");
        assertThat(oldCache.getVersion()).isEqualTo(1);
    }

    @Test
    void analyze_context발화는_speechStartedAt_asc_id_asc로_정렬() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("테스트 회의").build();
        Utterance second = utterance(meeting, 2L, "B", "두번째", 20);
        Utterance first = utterance(meeting, 1L, "A", "첫번째", 10);

        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(List.of(second, first));
        given(glmClient.chat(anyString(), anyString())).willReturn(emptyAnalysisJson());
        given(objectMapper.readValue(anyString(), any(Class.class)))
                .willAnswer(inv -> realMapper.readValue((String) inv.getArgument(0), AnalysisResult.class));

        service.analyze(1L);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(glmClient).chat(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue()).containsSubsequence("첫번째", "두번째");
    }

    @Test
    void analyze_glm응답_마크다운코드블록_제거후_파싱() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("테스트 회의").build();
        String glmResponseWithCodeBlock = """
                ```json
                {
                  "summary": "마크다운 포함 응답",
                  "unresolvedItems": "미결 사항 있음",
                  "worklogs": [],
                  "decisions": []
                }
                ```""";

        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(List.of());
        given(glmClient.chat(anyString(), anyString())).willReturn(glmResponseWithCodeBlock);
        given(objectMapper.readValue(anyString(), any(Class.class)))
                .willAnswer(inv -> realMapper.readValue((String) inv.getArgument(0), AnalysisResult.class));

        service.analyze(1L);

        ArgumentCaptor<UpdateContextRequest> captor = ArgumentCaptor.forClass(UpdateContextRequest.class);
        verify(contextService).updateContext(org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        assertThat(captor.getValue().summary()).isEqualTo("마크다운 포함 응답");
        assertThat(captor.getValue().unresolvedItems()).isEqualTo("미결 사항 있음");
    }

    @Test
    void analyze_glm응답_파싱실패시_예외발생() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("테스트 회의").build();

        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(List.of());
        given(glmClient.chat(anyString(), anyString())).willReturn("이건 JSON이 아닙니다");
        given(objectMapper.readValue(anyString(), any(Class.class)))
                .willAnswer(inv -> realMapper.readValue((String) inv.getArgument(0), AnalysisResult.class));

        assertThatThrownBy(() -> service.analyze(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("GLM 응답 파싱 실패");
    }

    @Test
    void analyze_프로젝트없는_회의면_ServiceException() {
        Meeting meeting = Meeting.builder().title("테스트 회의").build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.analyze(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("회의에 연결된 프로젝트가 없습니다.");
    }

    private String emptyAnalysisJson() {
        return """
                {
                  "summary": "요약",
                  "unresolvedItems": null,
                  "worklogs": [],
                  "decisions": []
                }
                """;
    }

    private Utterance utterance(Meeting meeting, Long id, String speakerName, String content, long speechOffset) {
        Utterance utterance = Utterance.builder()
                .meeting(meeting)
                .speakerName(speakerName)
                .speakerDiscordId("user-" + id)
                .content(content)
                .speechStartedAt(LocalDateTime.of(2026, 5, 4, 10, 0).plusSeconds(speechOffset))
                .build();
        ReflectionTestUtils.setField(utterance, "id", id);
        return utterance;
    }

    private Project mockProject(Long id) {
        Project project = org.mockito.Mockito.mock(Project.class);
        org.mockito.Mockito.lenient().when(project.getId()).thenReturn(id);
        return project;
    }
}
