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
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.support.AbstractPostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UtteranceRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UtteranceRepository utteranceRepository;

    @Autowired
    private TestEntityManager em;

    private Meeting meeting;

    @BeforeEach
    void setUp() {
        Project project = em.persist(Project.builder().name("테스트 프로젝트").build());
        meeting = em.persist(Meeting.builder().project(project).build());
        em.flush();
    }

    @Test
    void 다른_meetingId의_발화는_조회안됨() {
        Meeting other = em.persist(Meeting.builder().build());
        em.persist(utterance(meeting, "Alice", "a1", "이 회의 발화"));
        em.persist(utterance(other, "Bob", "b1", "다른 회의 발화"));
        em.flush();

        List<Utterance> result = utteranceRepository.findTop20ByMeetingIdOrderBySpokenAtDesc(meeting.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSpeakerName()).isEqualTo("Alice");
    }

    @Test
    void 발화가_25개면_최근_20개만_반환() {
        for (int i = 0; i < 25; i++) {
            em.persist(utterance(meeting, "user" + i, "discord" + i, "내용 " + i));
        }
        em.flush();

        List<Utterance> result = utteranceRepository.findTop20ByMeetingIdOrderBySpokenAtDesc(meeting.getId());

        assertThat(result).hasSize(20);
    }

    private Utterance utterance(Meeting m, String name, String discordId, String content) {
        return Utterance.builder()
                .meeting(m)
                .speakerName(name)
                .speakerDiscordId(discordId)
                .content(content)
                .sequenceNo((long) (Math.random() * 100000))
                .build();
    }
}
