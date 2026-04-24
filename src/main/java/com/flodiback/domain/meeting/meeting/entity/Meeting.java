package com.flodiback.domain.meeting.meeting.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.global.enums.MeetingStatus;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meetings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "title", length = 200)
    private String title;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MeetingStatus status;

    @Builder
    public Meeting(Project project, String title, MeetingStatus status) {
        this.project = project;
        this.title = title;
        this.status = status != null ? status : MeetingStatus.IN_PROGRESS;
    }
}
