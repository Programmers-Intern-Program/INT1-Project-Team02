package com.flodiback.api.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.global.embedding.OpenAiEmbeddingClient;

@Testcontainers
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "openai.api-key=test-key",
            "internal.api-key=test-internal-key"
        })
@AutoConfigureMockMvc
class MeetingControllerTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("flodi_test")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("init-pgvector.sql");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    private OpenAiEmbeddingClient embeddingClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private final List<Long> testMeetingIds = new ArrayList<>();
    private final List<Long> testProjectIds = new ArrayList<>();

    private Project project;

    @BeforeEach
    void setUp() {
        testMeetingIds.clear();
        testProjectIds.clear();
        project = saveProject("플로디");
    }

    @AfterEach
    void tearDown() {
        if (!testMeetingIds.isEmpty()) {
            meetingRepository.deleteAllByIdInBatch(testMeetingIds);
            testMeetingIds.clear();
        }
        if (!testProjectIds.isEmpty()) {
            projectRepository.deleteAllByIdInBatch(testProjectIds);
            testProjectIds.clear();
        }
    }

    @Test
    void getMeetings_projectId로_회의_목록을_조회한다() throws Exception {
        saveMeeting(project, "1차 기획 회의");
        saveMeeting(project, "2차 개발 회의");

        mockMvc.perform(get("/internal/v1/meetings")
                        .param("projectId", project.getId().toString())
                        .header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("1차 기획 회의"))
                .andExpect(jsonPath("$.data[1].title").value("2차 개발 회의"));
    }

    @Test
    void deleteMeeting_회의를_삭제한다() throws Exception {
        Meeting meeting = saveMeeting(project, "삭제할 회의");

        mockMvc.perform(delete("/internal/v1/meetings/{id}", meeting.getId())
                        .header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));

        assertThat(meetingRepository.findById(meeting.getId())).isEmpty();
        testMeetingIds.remove(meeting.getId());
    }

    private Meeting saveMeeting(Project project, String title) {
        Meeting saved = meetingRepository.save(
                Meeting.builder().project(project).title(title).build());
        testMeetingIds.add(saved.getId());
        return saved;
    }

    private Project saveProject(String name) {
        Project saved = projectRepository.save(Project.builder().name(name).build());
        testProjectIds.add(saved.getId());
        return saved;
    }
}
