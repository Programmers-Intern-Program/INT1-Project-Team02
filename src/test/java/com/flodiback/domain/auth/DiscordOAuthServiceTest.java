package com.flodiback.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.flodiback.domain.server.server.repository.DiscordServerRepository;

class DiscordOAuthServiceTest {

    @Test
    void buildAuthorizationUrl_includesEncodedStateWhenProvided() {
        DiscordOAuthService service = new DiscordOAuthService(
                "client-id",
                "client-secret",
                "https://api.example/auth/v1/discord/callback",
                mock(DiscordServerRepository.class));

        String url = service.buildAuthorizationUrl("/channels/123/dashboard?server_id=1&guild_id=1");

        assertThat(url).contains("state=%2Fchannels%2F123%2Fdashboard%3Fserver_id%3D1%26guild_id%3D1");
    }

    @Test
    void buildAuthorizationUrl_omitsStateWhenNotProvided() {
        DiscordOAuthService service = new DiscordOAuthService(
                "client-id",
                "client-secret",
                "https://api.example/auth/v1/discord/callback",
                mock(DiscordServerRepository.class));

        assertThat(service.buildAuthorizationUrl()).doesNotContain("state=");
    }
}
