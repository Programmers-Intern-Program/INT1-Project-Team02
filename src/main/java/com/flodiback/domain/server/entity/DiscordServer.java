package com.flodiback.domain.server.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "discord_servers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscordServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, unique = true, length = 50)
    private String guildId;

    @Column(name = "guild_name", nullable = false, length = 100)
    private String guildName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public DiscordServer(String guildId, String guildName) {
        this.guildId = guildId;
        this.guildName = guildName;
    }
}
