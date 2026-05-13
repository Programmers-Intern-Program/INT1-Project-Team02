package com.flodiback.domain.project.project.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.flodiback.domain.server.server.entity.DiscordServer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "projects")
@SQLDelete(sql = "UPDATE projects SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
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

    @Column(name = "channel_id", length = 50)
    private String channelId;

    @Column(name = "channel_name", length = 100)
    private String channelName;

    @Column(name = "owner_discord_id", length = 50)
    private String ownerDiscordId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tech_stack", length = 255)
    private String techStack;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void update(String name, String description, String techStack) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (techStack != null) this.techStack = techStack;
    }

    public void connectChannel(String channelId, String channelName) {
        this.channelId = channelId;
        this.channelName = channelName;
    }

    public void disconnectChannel() {
        this.channelId = null;
        this.channelName = null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    @Builder
    public Project(
            DiscordServer server,
            String channelId,
            String ownerDiscordId,
            String name,
            String description,
            String techStack,
            String metadata) {
        this.server = server;
        this.channelId = channelId;
        this.ownerDiscordId = ownerDiscordId;
        this.name = name;
        this.description = description;
        this.techStack = techStack;
        this.metadata = metadata;
    }
}
