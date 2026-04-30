package com.flodiback.bot;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flodiback.bot.command.DiscordCommandListener;
import com.flodiback.domain.speech.stt.SttProvider;
import com.flodiback.domain.speech.stt.provider.openai.OpenAiSttProvider;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.audio.AudioNatives;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

/**
 * Discord 봇 전용 실행 진입점.
 *
 * <p>Spring 서버(FlodiBackApplication)와 분리해서 실행할 수 있도록 별도 main을 둔다.
 */
public final class DiscordBotMain {
    private static final Logger log = LoggerFactory.getLogger(DiscordBotMain.class);

    private DiscordBotMain() {}

    public static void main(String[] args) throws InterruptedException {
        // 1) 토큰/설정값 로드
        String token = resolveToken();
        String prefix = BotEnv.getOrDefault("DISCORD_BOT_PREFIX", "!");
        long defaultMeetingId = parseMeetingId(BotEnv.getOrDefault("DISCORD_DEFAULT_MEETING_ID", "1"));

        // 2) Opus native 로딩 상태 확인
        // canReceiveUser/Opus decode 경로는 native 준비 여부에 영향받는다.
        boolean opusReady = AudioNatives.ensureOpus();
        log.info(
                "Audio natives status. supported={}, initialized={}, opusReady={}",
                AudioNatives.isAudioSupported(),
                AudioNatives.isInitialized(),
                opusReady);

        // 3) 게이트웨이 인텐트 범위 설정
        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_VOICE_STATES);

        // 4) DAVE(E2EE) 세션 팩토리 설정
        AudioModuleConfig audioModuleConfig = new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory());

        // 5) STT provider 인스턴스 준비
        SttProvider sttProvider = new OpenAiSttProvider();

        // 6) JDA 빌드 + 명령 리스너 등록
        JDA jda = JDABuilder.createDefault(token, intents)
                .enableCache(CacheFlag.VOICE_STATE)
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                .disableCache(
                        CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SOUNDBOARD_SOUNDS, CacheFlag.SCHEDULED_EVENTS)
                .setAudioModuleConfig(audioModuleConfig)
                .addEventListeners(new DiscordCommandListener(prefix, sttProvider, defaultMeetingId))
                .build()
                .awaitReady();

        log.info(
                "Discord bot ready. userId={}, username={}, prefix={}, defaultMeetingId={}",
                jda.getSelfUser().getId(),
                jda.getSelfUser().getName(),
                prefix,
                defaultMeetingId);
    }

    private static String resolveToken() {
        String token = sanitizeToken(BotEnv.get("DISCORD_TOKEN"));
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISCORD_TOKEN 환경변수 또는 .env 값이 비어있습니다.");
        }
        return token;
    }

    /**
     * 환경변수 문자열을 long meetingId로 안전하게 변환한다.
     */
    private static long parseMeetingId(String rawMeetingId) {
        try {
            return Long.parseLong(rawMeetingId.trim());
        } catch (Exception exception) {
            throw new IllegalStateException("DISCORD_DEFAULT_MEETING_ID 값이 숫자가 아닙니다: " + rawMeetingId, exception);
        }
    }

    /**
     * 토큰 문자열 정규화.
     *
     * <p>예: "token", 'token', "Bot token" 형태를 모두 정리한다.
     */
    private static String sanitizeToken(String token) {
        if (token == null) {
            return null;
        }

        String normalized = token.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.regionMatches(true, 0, "Bot ", 0, 4)) {
            normalized = normalized.substring(4).trim();
        }
        return normalized;
    }
}
