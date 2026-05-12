package com.flodiback.domain.server.server.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.server.server.entity.DiscordServer;

public interface DiscordServerRepository extends JpaRepository<DiscordServer, Long> {

    Optional<DiscordServer> findByGuildId(String guildId);
}
