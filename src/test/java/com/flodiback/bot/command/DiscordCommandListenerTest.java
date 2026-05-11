package com.flodiback.bot.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordCommandListenerTest {

    @Test
    void parseAllowedGuildIds_returnsEmptySetForBlankValue() {
        assertThat(DiscordCommandListener.parseAllowedGuildIds(null)).isEmpty();
        assertThat(DiscordCommandListener.parseAllowedGuildIds("   ")).isEmpty();
    }

    @Test
    void parseAllowedGuildIds_parsesCommaSeparatedGuildIds() {
        assertThat(DiscordCommandListener.parseAllowedGuildIds("123, 456,,789"))
                .containsExactlyInAnyOrder(123L, 456L, 789L);
    }

    @Test
    void parseAllowedGuildIds_ignoresInvalidTokens() {
        assertThat(DiscordCommandListener.parseAllowedGuildIds("123,abc,456")).containsExactlyInAnyOrder(123L, 456L);
    }
}
