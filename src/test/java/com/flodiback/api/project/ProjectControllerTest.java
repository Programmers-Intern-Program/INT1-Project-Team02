package com.flodiback.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
class ProjectControllerTest {

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
    private ProjectRepository projectRepository;

    private final List<Long> testProjectIds = new ArrayList<>();

    private Project project;

    @BeforeEach
    void setUp() {
        testProjectIds.clear();
        project = saveProject("플로디", null, null);
    }

    @AfterEach
    void tearDown() {
        if (!testProjectIds.isEmpty()) {
            projectRepository.deleteAllByIdInBatch(testProjectIds);
            testProjectIds.clear();
        }
    }

    @Test
    void connectChannel_채널을_프로젝트에_연결한다() throws Exception {
        mockMvc.perform(put("/api/v1/projects/{id}/channel/{channelId}", project.getId(), "channel-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));

        Project updated = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(updated.getChannelId()).isEqualTo("channel-123");
    }

    @Test
    void disconnectChannel_채널_연결을_해제한다() throws Exception {
        project.connectChannel("channel-123");
        projectRepository.save(project);

        mockMvc.perform(delete("/api/v1/projects/{id}/channel", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));

        Project updated = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(updated.getChannelId()).isNull();
    }

    @Test
    void getByChannelId_채널에_연결된_프로젝트를_조회한다() throws Exception {
        project.connectChannel("channel-123");
        projectRepository.save(project);

        mockMvc.perform(get("/api/v1/projects/channel/{channelId}", "channel-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.name").value("플로디"));
    }

    @Test
    void getByChannelId_연결된_프로젝트가_없으면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/projects/channel/{channelId}", "없는채널"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }

    private Project saveProject(String name, String description, String techStack) {
        Project saved = projectRepository.save(Project.builder()
                .name(name)
                .description(description)
                .techStack(techStack)
                .build());
        testProjectIds.add(saved.getId());
        return saved;
    }
}
