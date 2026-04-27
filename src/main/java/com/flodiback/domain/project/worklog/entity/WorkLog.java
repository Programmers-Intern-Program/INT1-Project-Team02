package com.flodiback.domain.project.worklog.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.flodiback.domain.meeting.entity.Meeting;
import com.flodiback.domain.project.entity.Project;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "work_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "assignee_name", nullable = false, length = 100)
    private String assigneeName;

    @Column(name = "task", nullable = false, columnDefinition = "TEXT")
    private String task;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public WorkLog(Meeting meeting, Project project, String assigneeName, String task, LocalDate dueDate) {
        this.meeting = meeting;
        this.project = project;
        this.assigneeName = assigneeName;
        this.task = task;
        this.dueDate = dueDate;
    }
}
