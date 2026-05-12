package com.flodiback.domain.project.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flodiback.domain.project.project.dto.CreateProjectRequest;
import com.flodiback.domain.project.project.dto.ProjectResponse;
import com.flodiback.domain.project.project.dto.UpdateProjectRequest;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.domain.server.server.entity.DiscordServer;
import com.flodiback.domain.server.server.repository.DiscordServerRepository;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @InjectMocks
    private ProjectService projectService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DiscordServerRepository discordServerRepository;

    // ─── create ───────────────────────────────────────────

    @Test
    void create_serverId없으면_server_null로_프로젝트생성() {
        // given
        CreateProjectRequest req = new CreateProjectRequest("플로디 프로젝트", "설명", "Java", null, null, null, null, null);
        given(projectRepository.save(any(Project.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ProjectResponse response = projectService.create(req);

        // then
        verify(discordServerRepository, never()).findById(any());
        verify(projectRepository).save(any(Project.class));
        assertThat(response.serverId()).isNull();
        assertThat(response.name()).isEqualTo("플로디 프로젝트");
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.techStack()).isEqualTo("Java");
    }

    @Test
    void create_유효한_serverId면_서버연결하여_프로젝트생성() {
        // given
        DiscordServer server =
                DiscordServer.builder().guildId("guild-123").guildName("플로디 서버").build();
        CreateProjectRequest req = new CreateProjectRequest("플로디 프로젝트", "설명", "Java", 1L, null, null, null, null);
        given(discordServerRepository.findById(1L)).willReturn(Optional.of(server));
        given(projectRepository.save(any(Project.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ProjectResponse response = projectService.create(req);

        // then
        verify(discordServerRepository).findById(1L);
        verify(projectRepository).save(any(Project.class));
        assertThat(response.name()).isEqualTo("플로디 프로젝트");
    }

    @Test
    void create_존재하지않는_serverId면_예외발생() {
        // given
        CreateProjectRequest req = new CreateProjectRequest("플로디 프로젝트", "설명", "Java", 999L, null, null, null, null);
        given(discordServerRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> projectService.create(req))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 서버입니다.");

        verify(projectRepository, never()).save(any());
    }

    // ─── getAll ───────────────────────────────────────────

    @Test
    void getAll_프로젝트목록반환() {
        // given
        Project p1 = Project.builder().name("프로젝트 A").build();
        Project p2 = Project.builder().name("프로젝트 B").build();
        willReturn(List.of(p1, p2)).given(projectRepository).findAll();

        // when
        List<ProjectResponse> responses = projectService.getAll();

        // then
        verify(projectRepository).findAll();
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).name()).isEqualTo("프로젝트 A");
        assertThat(responses.get(1).name()).isEqualTo("프로젝트 B");
    }

    // ─── getById ──────────────────────────────────────────

    @Test
    void getById_유효한_id면_프로젝트반환() {
        // given
        Project project = Project.builder()
                .name("플로디 프로젝트")
                .description("설명")
                .techStack("Java")
                .build();
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));

        // when
        ProjectResponse response = projectService.getById(1L, null);

        // then
        assertThat(response.name()).isEqualTo("플로디 프로젝트");
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.techStack()).isEqualTo("Java");
    }

    @Test
    void getById_존재하지않는_id면_예외발생() {
        // given
        given(projectRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> projectService.getById(999L, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 프로젝트입니다.");
    }

    // ─── update ───────────────────────────────────────────

    @Test
    void update_유효한_id면_프로젝트수정() {
        // given
        Project project = Project.builder()
                .name("기존 이름")
                .description("기존 설명")
                .techStack("Java")
                .build();
        UpdateProjectRequest req = new UpdateProjectRequest("새 이름", "새 설명", "Kotlin");
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));

        // when
        ProjectResponse response = projectService.update(1L, req, null);

        // then
        assertThat(response.name()).isEqualTo("새 이름");
        assertThat(response.description()).isEqualTo("새 설명");
        assertThat(response.techStack()).isEqualTo("Kotlin");
    }

    @Test
    void update_존재하지않는_id면_예외발생() {
        // given
        given(projectRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> projectService.update(999L, new UpdateProjectRequest("이름", null, null), null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 프로젝트입니다.");
    }

    // ─── delete ───────────────────────────────────────────

    @Test
    void delete_유효한_id면_소프트삭제() {
        // given
        Project project = Project.builder().name("플로디 프로젝트").build();
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));

        // when
        projectService.delete(1L, null);

        // then
        assertThat(project.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_존재하지않는_id면_예외발생() {
        // given
        given(projectRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> projectService.delete(999L, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 프로젝트입니다.");
    }
}
