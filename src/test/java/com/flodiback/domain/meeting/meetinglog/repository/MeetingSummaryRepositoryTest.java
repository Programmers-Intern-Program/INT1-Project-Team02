package com.flodiback.domain.meeting.meetinglog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.support.AbstractPostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MeetingSummaryRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MeetingSummaryRepository meetingSummaryRepository;

    @Autowired
    private TestEntityManager em;

    private Project project;
    private Meeting currentMeeting;

    @BeforeEach
    void setUp() {
        project = em.persist(Project.builder().name("테스트 프로젝트").build());
        currentMeeting = em.persist(Meeting.builder().project(project).build());
        em.flush();
    }

    @Test
    void 현재_회의의_summary는_결과에서_제외() {
        em.persist(summary(currentMeeting, "현재 회의 요약"));
        em.flush();

        List<MeetingSummary> result =
                meetingSummaryRepository.findPastByProjectId(project.getId(), currentMeeting.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void 같은_프로젝트의_과거_회의_summary는_반환() {
        Meeting pastMeeting = em.persist(Meeting.builder().project(project).build());
        em.persist(summary(pastMeeting, "과거 회의 요약"));
        em.flush();

        List<MeetingSummary> result =
                meetingSummaryRepository.findPastByProjectId(project.getId(), currentMeeting.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSummary()).isEqualTo("과거 회의 요약");
    }

    @Test
    void 다른_프로젝트의_summary는_조회안됨() {
        Project otherProject = em.persist(Project.builder().name("다른 프로젝트").build());
        Meeting otherMeeting =
                em.persist(Meeting.builder().project(otherProject).build());
        em.persist(summary(otherMeeting, "다른 프로젝트 요약"));
        em.flush();

        List<MeetingSummary> result =
                meetingSummaryRepository.findPastByProjectId(project.getId(), currentMeeting.getId());

        assertThat(result).isEmpty();
    }

    private MeetingSummary summary(Meeting meeting, String text) {
        return MeetingSummary.builder().meeting(meeting).summary(text).build();
    }
}
