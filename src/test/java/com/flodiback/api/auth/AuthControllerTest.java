package com.flodiback.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthControllerTest {

    @Test
    void safeRedirectPathOrDefault_allowsRelativeDashboardPathWithQuery() {
        assertThat(AuthController.safeRedirectPathOrDefault("/channels/123/dashboard?server_id=1&guild_id=1"))
                .isEqualTo("/channels/123/dashboard?server_id=1&guild_id=1");
    }

    @Test
    void safeRedirectPathOrDefault_fallsBackForBlankOrExternalUrls() {
        assertThat(AuthController.safeRedirectPathOrDefault(null)).isEqualTo("/projects");
        assertThat(AuthController.safeRedirectPathOrDefault("")).isEqualTo("/projects");
        assertThat(AuthController.safeRedirectPathOrDefault("https://evil.example"))
                .isEqualTo("/projects");
        assertThat(AuthController.safeRedirectPathOrDefault("//evil.example")).isEqualTo("/projects");
    }

    @Test
    void safeRedirectPathOrDefault_fallsBackForControlCharacters() {
        assertThat(AuthController.safeRedirectPathOrDefault("/channels/123\nSet-Cookie:bad"))
                .isEqualTo("/projects");
    }
}
