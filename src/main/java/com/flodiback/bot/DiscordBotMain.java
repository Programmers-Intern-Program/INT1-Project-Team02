package com.flodiback.bot;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flodiback.bot.command.DiscordCommandListener;

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
    // SLF4J 로거 팩토리: 클래스 이름 기준 로거를 생성해 콘솔/파일 로그 백엔드(Logback)로 전달한다.
    private static final Logger log = LoggerFactory.getLogger(DiscordBotMain.class);

    private DiscordBotMain() {}

    public static void main(String[] args) throws InterruptedException {
        // 1) 토큰을 환경변수/.env에서 읽고 정리한다.
        String token = resolveToken();

        // 2) Opus native 로딩 가능 여부를 선체크한다.
        // canReceiveUser/PCM 디코딩은 Opus native가 없으면 동작하지 않는다.
        boolean opusReady = AudioNatives.ensureOpus();
        log.info(
                "Audio natives status. supported={}, initialized={}, opusReady={}",
                AudioNatives.isAudioSupported(),
                AudioNatives.isInitialized(),
                opusReady);

        // 3) 이 봇이 구독할 게이트웨이 이벤트 범위를 선언한다.
        // - GUILD_MESSAGES: 서버 메시지 이벤트
        // - MESSAGE_CONTENT: 메시지 본문 읽기 (!ping 같은 prefix 명령 파싱용)
        // - GUILD_VOICE_STATES: 음성 채널 입퇴장/상태 이벤트
        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_VOICE_STATES);

        // 4) DAVE(E2EE) 세션 팩토리를 오디오 모듈에 연결한다.
        AudioModuleConfig audioModuleConfig = new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory());

        // 5) JDA 클라이언트를 빌드하고 ready 상태까지 대기한다.
        JDA jda = JDABuilder.createDefault(token, intents)
                // 음성 상태 캐시는 명령 처리(!join/!leave)에서 필요하다.
                .enableCache(CacheFlag.VOICE_STATE)
                // canReceiveUser(UserAudio)는 음성 참여 멤버 캐시가 필요하다.
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                // 사용하지 않는 캐시는 끄고 경고 로그를 줄인다.
                .disableCache(
                        CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SOUNDBOARD_SOUNDS, CacheFlag.SCHEDULED_EVENTS)
                // DAVE 모듈 적용
                .setAudioModuleConfig(audioModuleConfig)
                // 텍스트 명령(!ping/!join/!leave/!stats) 리스너 등록
                .addEventListeners(new DiscordCommandListener())
                .build()
                .awaitReady();

        log.info(
                "Discord bot ready. userId={}, username={}",
                jda.getSelfUser().getId(),
                jda.getSelfUser().getName());
    }

    private static String resolveToken() {
        // DISCORD_TOKEN 값을 .env fallback 로더를 통해 읽는다.
        String token = sanitizeToken(BotEnv.get("DISCORD_TOKEN"));
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISCORD_TOKEN 환경변수 또는 .env 값이 비어있습니다.");
        }
        return token;
    }

    private static String sanitizeToken(String token) {
        // 실행 환경마다 토큰 입력 포맷이 달라질 수 있어 사전 정규화한다.
        // 예) "token", 'token', Bot token
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
