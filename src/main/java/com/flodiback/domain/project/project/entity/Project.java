package com.flodiback.domain.project.project.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.flodiback.domain.server.server.entity.DiscordServer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ON DELETE SET NULL → nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = true)
    private DiscordServer server;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tech_stack", length = 255)
    private String techStack;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Project(DiscordServer server, String name, String description, String techStack) {
        this.server = server;
        this.name = name;
        this.description = description;
        this.techStack = techStack;
    }
}
