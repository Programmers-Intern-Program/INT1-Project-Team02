package com.flodiback.domain.project.worklog.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.project.project.entity.Project;

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

    private static final Set<String> SUPPORTED_STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE", "CANCELLED");

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

    @Column(name = "assignee_discord_id", length = 50)
    private String assigneeDiscordId;

    @Column(name = "task", nullable = false, columnDefinition = "TEXT")
    private String task;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public WorkLog(
            Meeting meeting,
            Project project,
            String assigneeName,
            String assigneeDiscordId,
            String task,
            LocalDate dueDate) {
        this.meeting = meeting;
        this.project = project;
        this.assigneeName = assigneeName;
        this.assigneeDiscordId = assigneeDiscordId;
        this.task = task;
        this.dueDate = dueDate;
        this.status = "TODO";
    }

    public void updateStatus(String newStatus) {
        String normalized = normalizeStatus(newStatus);
        if (normalized != null) {
            this.status = normalized;
        }
    }

    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.strip().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 작업 로그 상태입니다: " + status);
        }
        return normalized;
    }
}
