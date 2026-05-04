package com.flodiback.api.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.global.embedding.OpenAiEmbeddingClient;

@Testcontainers
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "openai.api-key=test-key"
        })
@AutoConfigureMockMvc
class DecisionControllerTest {

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
        // Decision.embedding의 vector(768) 타입을 실제 PostgreSQL 확장에서 검증합니다.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    private OpenAiEmbeddingClient embeddingClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    private final List<Long> testProjectIds = new ArrayList<>();

    private Project project;
    private Project otherProject;

    @BeforeEach
    void setUp() {
        testProjectIds.clear();

        // MockMvc 요청에서도 조회되도록 Repository로 테스트 프로젝트를 먼저 저장합니다.
        project = saveProject("Flodi", "Discord AI 회의 에이전트");
        otherProject = saveProject("Other", "다른 프로젝트");
    }

    @AfterEach
    void tearDown() {
        if (testProjectIds.isEmpty()) {
            return;
        }

        // 기존 데이터를 보호하기 위해 테스트가 만든 프로젝트에 속한 결정사항만 삭제합니다.
        List<Long> testDecisionIds = testProjectIds.stream()
                .flatMap(projectId -> decisionRepository.findByProjectId(projectId).stream())
                .map(Decision::getId)
                .toList();

        if (!testDecisionIds.isEmpty()) {
            decisionRepository.deleteAllByIdInBatch(testDecisionIds);
        }

        projectRepository.deleteAllByIdInBatch(testProjectIds);
        testProjectIds.clear();
    }

    @Test
    void getDecisions_returnsProjectDecisionList() throws Exception {
        saveDecision(project, "인증 방식은 JWT로 한다.");
        saveDecision(project, "배포는 AWS로 한다.");
        saveDecision(otherProject, "다른 프로젝트 결정사항");

        mockMvc.perform(get("/api/v1/projects/{projectId}/decisions", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content").value("인증 방식은 JWT로 한다."))
                .andExpect(jsonPath("$.data[1].content").value("배포는 AWS로 한다."));
    }

    @Test
    void createDecision_savesDecision() throws Exception {
        String requestBody = """
                {
                  "content": "인증 방식은 JWT로 한다."
                }
                """;

        mockMvc.perform(post("/api/v1/projects/{projectId}/decisions", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.projectId").value(project.getId()))
                .andExpect(jsonPath("$.data.meetingId").doesNotExist())
                .andExpect(jsonPath("$.data.content").value("인증 방식은 JWT로 한다."));

        List<Decision> decisions = decisionRepository.findByProjectId(project.getId());
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getContent()).isEqualTo("인증 방식은 JWT로 한다.");
    }

    @Test
    void updateDecision_updatesOnlyProjectDecision() throws Exception {
        Decision decision = saveDecision(project, "인증 방식은 OAuth로 한다.");

        String requestBody = """
                {
                  "content": "인증 방식은 JWT로 한다."
                }
                """;

        mockMvc.perform(put("/api/v1/projects/{projectId}/decisions/{decisionId}", project.getId(), decision.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.content").value("인증 방식은 JWT로 한다."));

        Decision updatedDecision = decisionRepository.findById(decision.getId()).orElseThrow();
        assertThat(updatedDecision.getContent()).isEqualTo("인증 방식은 JWT로 한다.");
    }

    @Test
    void deleteDecision_deletesOnlyProjectDecision() throws Exception {
        Decision decision = saveDecision(project, "삭제할 결정사항");

        mockMvc.perform(delete(
                        "/api/v1/projects/{projectId}/decisions/{decisionId}", project.getId(), decision.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));

        assertThat(decisionRepository.findById(decision.getId())).isEmpty();
    }

    @Test
    void createDecision_returnsNotFound_whenProjectDoesNotExist() throws Exception {
        String requestBody = """
                {
                  "content": "인증 방식은 JWT로 한다."
                }
                """;

        mockMvc.perform(post("/api/v1/projects/{projectId}/decisions", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }

    @Test
    void updateDecision_returnsNotFound_whenDecisionBelongsToAnotherProject() throws Exception {
        Decision decision = saveDecision(otherProject, "다른 프로젝트 결정사항");

        String requestBody = """
                {
                  "content": "수정되면 안 되는 결정사항"
                }
                """;

        mockMvc.perform(put("/api/v1/projects/{projectId}/decisions/{decisionId}", project.getId(), decision.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }

    @Test
    void deleteDecision_returnsNotFound_whenDecisionBelongsToAnotherProject() throws Exception {
        Decision decision = saveDecision(otherProject, "다른 프로젝트 결정사항");

        mockMvc.perform(delete(
                        "/api/v1/projects/{projectId}/decisions/{decisionId}", project.getId(), decision.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }

    @Test
    void createDecision_returnsBadRequest_whenContentIsBlank() throws Exception {
        String requestBody = """
                {
                  "content": ""
                }
                """;

        mockMvc.perform(post("/api/v1/projects/{projectId}/decisions", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }

    private Decision saveDecision(Project project, String content) {
        return decisionRepository.save(
                Decision.builder().project(project).content(content).build());
    }

    private Project saveProject(String name, String description) {
        Project savedProject = projectRepository.save(Project.builder()
                .name(name)
                .description(description)
                .techStack("Spring Boot")
                .build());
        testProjectIds.add(savedProject.getId());

        return savedProject;
    }
}
