package com.flodiback.api.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.speech.service.SpeechAiAnswerService;
import com.flodiback.global.enums.MeetingStatus;
import com.flodiback.support.AbstractPostgresIntegrationTest;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@Transactional
class InternalSpeechControllerTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UtteranceRepository utteranceRepository;

    @MockitoBean
    private SpeechAiAnswerService speechAiAnswerService;

    private Meeting meeting;

    @BeforeEach
    void setUp() {
        // 발화 저장 테스트를 위해 최소 프로젝트와 회의를 먼저 만든다.
        Project project = Project.builder()
                .name("Flodi")
                .description("Discord AI 회의 에이전트")
                .techStack("Spring Boot")
                .build();
        entityManager.persist(project);

        meeting = Meeting.builder()
                .project(project)
                .title("MVP 회의")
                .status(MeetingStatus.IN_PROGRESS)
                .build();
        entityManager.persist(meeting);
        entityManager.flush();
    }

    @Test
    void receiveSpeech_savesUtteranceFromSnakeCaseRequest() throws Exception {
        String requestBody = """
                {
                  "meeting_id": %d,
                  "speaker_discord_id": "123456789",
                  "speaker_name": "김철수",
                  "text": "이번 스프린트 목표를 어떻게 잡을까요?",
                  "timestamp": "2026-04-23T10:30:00"
                }
                """.formatted(meeting.getId());

        mockMvc.perform(post("/internal/v1/speech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.meeting_id").value(meeting.getId()))
                .andExpect(jsonPath("$.data.ai_answer").value(nullValue()));

        List<Utterance> utterances = utteranceRepository.findAll();
        assertThat(utterances).hasSize(1);

        Utterance savedUtterance = utterances.get(0);
        assertThat(savedUtterance.getMeeting().getId()).isEqualTo(meeting.getId());
        assertThat(savedUtterance.getSpeakerDiscordId()).isEqualTo("123456789");
        assertThat(savedUtterance.getSpeakerName()).isEqualTo("김철수");
        assertThat(savedUtterance.getContent()).isEqualTo("이번 스프린트 목표를 어떻게 잡을까요?");
        assertThat(savedUtterance.getSpokenAt()).isEqualTo(LocalDateTime.of(2026, 4, 23, 10, 30));
    }

    @Test
    void receiveSpeech_returnsNotFound_whenMeetingDoesNotExist() throws Exception {
        String requestBody = """
                {
                  "meeting_id": 999999,
                  "speaker_discord_id": "123456789",
                  "speaker_name": "김철수",
                  "text": "회의가 없는 경우입니다.",
                  "timestamp": "2026-04-23T10:30:00"
                }
                """;

        mockMvc.perform(post("/internal/v1/speech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }

    @Test
    void receiveSpeech_returnsBadRequest_whenRequiredTextIsBlank() throws Exception {
        String requestBody = """
                {
                  "meeting_id": %d,
                  "speaker_discord_id": "123456789",
                  "speaker_name": "김철수",
                  "text": "",
                  "timestamp": "2026-04-23T10:30:00"
                }
                """.formatted(meeting.getId());

        mockMvc.perform(post("/internal/v1/speech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }

    @Test
    void receiveSpeech_returnsAiAnswer_whenWakeWordIsIncluded() throws Exception {
        given(speechAiAnswerService.generateAnswerIfCalled(anyLong(), anyString()))
                .willReturn("인증 방식은 JWT로 결정했습니다.");

        String requestBody = """
                {
                  "meeting_id": %d,
                  "speaker_discord_id": "123456789",
                  "speaker_name": "김철수",
                  "text": "AI야, 인증 방식 뭐로 하기로 했지?",
                  "timestamp": "2026-04-23T10:30:00"
                }
                """.formatted(meeting.getId());

        mockMvc.perform(post("/internal/v1/speech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.ai_answer").value("인증 방식은 JWT로 결정했습니다."));

        List<Utterance> utterances = utteranceRepository.findAll();
        assertThat(utterances).hasSize(1);
        assertThat(utterances.get(0).getContent()).isEqualTo("AI야, 인증 방식 뭐로 하기로 했지?");
        verify(speechAiAnswerService).generateAnswerIfCalled(meeting.getId(), "AI야, 인증 방식 뭐로 하기로 했지?");
    }

    @Test
    void receiveSpeech_savesOnly_whenWakeWordHasNoQuestion() throws Exception {
        String requestBody = """
                {
                  "meeting_id": %d,
                  "speaker_discord_id": "123456789",
                  "speaker_name": "김철수",
                  "text": "AI야",
                  "timestamp": "2026-04-23T10:30:00"
                }
                """.formatted(meeting.getId());

        mockMvc.perform(post("/internal/v1/speech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.ai_answer").value(nullValue()));

        List<Utterance> utterances = utteranceRepository.findAll();
        assertThat(utterances).hasSize(1);
        assertThat(utterances.get(0).getContent()).isEqualTo("AI야");
    }

    @Test
    void receiveSpeech_savesSpeech_whenAiAnswerIsNull() throws Exception {
        String requestBody = """
                {
                  "meeting_id": %d,
                  "speaker_discord_id": "123456789",
                  "speaker_name": "김철수",
                  "text": "봇아, 토큰 만료 시간 정했어?",
                  "timestamp": "2026-04-23T10:30:00"
                }
                """.formatted(meeting.getId());

        mockMvc.perform(post("/internal/v1/speech")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.ai_answer").value(nullValue()));

        List<Utterance> utterances = utteranceRepository.findAll();
        assertThat(utterances).hasSize(1);
        assertThat(utterances.get(0).getContent()).isEqualTo("봇아, 토큰 만료 시간 정했어?");
        verify(speechAiAnswerService).generateAnswerIfCalled(meeting.getId(), "봇아, 토큰 만료 시간 정했어?");
    }
}
