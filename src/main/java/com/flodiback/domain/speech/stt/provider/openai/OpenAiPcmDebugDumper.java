package com.flodiback.domain.speech.stt.provider.openai;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flodiback.bot.BotEnv;

final class OpenAiPcmDebugDumper {
    private static final Logger log = LoggerFactory.getLogger(OpenAiPcmDebugDumper.class);

    private static final AudioFormat RAW_FORMAT = new AudioFormat(48_000f, 16, 2, true, true);
    private static final AudioFormat REALTIME_FORMAT = new AudioFormat(24_000f, 16, 1, true, false);

    private final boolean enabled;
    private final Path outputDir;
    private final int maxRawBytes;
    private final int maxRealtimeBytes;
    private final Map<String, SessionBuffers> buffersBySessionId = new ConcurrentHashMap<>();

    OpenAiPcmDebugDumper() {
        this.enabled = Boolean.parseBoolean(BotEnv.getOrDefault("STT_DEBUG_DUMP_WAV", "false"));
        this.outputDir = Path.of(BotEnv.getOrDefault("STT_DEBUG_DUMP_DIR", "debug/stt-pcm"));
        int dumpSeconds = parsePositiveInt(BotEnv.getOrDefault("STT_DEBUG_DUMP_SECONDS", "5"), 5);
        this.maxRawBytes = (int) (RAW_FORMAT.getFrameRate() * RAW_FORMAT.getFrameSize() * dumpSeconds);
        this.maxRealtimeBytes = (int) (REALTIME_FORMAT.getFrameRate() * REALTIME_FORMAT.getFrameSize() * dumpSeconds);

        if (enabled) {
            log.info(
                    "[PCM덤프/활성화] outputDir={}, dumpSeconds={}, rawFormat=48k_stereo_pcm16be, realtimeFormat=24k_mono_pcm16le",
                    outputDir,
                    dumpSeconds);
        }
    }

    void capture(String sessionId, byte[] rawPcm, byte[] realtimePcm) {
        if (!enabled) {
            return;
        }
        SessionBuffers buffers = buffersBySessionId.computeIfAbsent(
                sessionId, ignored -> new SessionBuffers(maxRawBytes, maxRealtimeBytes));
        buffers.appendRaw(rawPcm);
        buffers.appendRealtime(realtimePcm);
    }

    void flushSession(String sessionId) {
        if (!enabled) {
            return;
        }
        SessionBuffers buffers = buffersBySessionId.remove(sessionId);
        if (buffers == null) {
            return;
        }

        try {
            Files.createDirectories(outputDir);
            Path rawPath = outputDir.resolve(sanitize(sessionId) + "-raw.wav");
            Path realtimePath = outputDir.resolve(sanitize(sessionId) + "-openai.wav");
            writeWav(buffers.rawBytes(), RAW_FORMAT, rawPath);
            writeWav(buffers.realtimeBytes(), REALTIME_FORMAT, realtimePath);
            log.info(
                    "[PCM덤프/저장완료] sessionId={}, rawPath={}, rawBytes={}, realtimePath={}, realtimeBytes={}",
                    sessionId,
                    rawPath,
                    buffers.rawBytes().length,
                    realtimePath,
                    buffers.realtimeBytes().length);
        } catch (Exception exception) {
            log.warn("[PCM덤프/저장실패] sessionId={}, outputDir={}", sessionId, outputDir, exception);
        }
    }

    private void writeWav(byte[] pcmBytes, AudioFormat format, Path outputPath) throws IOException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(pcmBytes);
                AudioInputStream audioInputStream =
                        new AudioInputStream(byteArrayInputStream, format, pcmBytes.length / format.getFrameSize())) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputPath.toFile());
        }
    }

    private int parsePositiveInt(String raw, int defaultValue) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String sanitize(String sessionId) {
        return sessionId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static final class SessionBuffers {
        private final ByteArrayOutputStream raw = new ByteArrayOutputStream();
        private final ByteArrayOutputStream realtime = new ByteArrayOutputStream();
        private final int maxRawBytes;
        private final int maxRealtimeBytes;

        private SessionBuffers(int maxRawBytes, int maxRealtimeBytes) {
            this.maxRawBytes = maxRawBytes;
            this.maxRealtimeBytes = maxRealtimeBytes;
        }

        synchronized void appendRaw(byte[] bytes) {
            appendWithLimit(raw, bytes, maxRawBytes);
        }

        synchronized void appendRealtime(byte[] bytes) {
            appendWithLimit(realtime, bytes, maxRealtimeBytes);
        }

        synchronized byte[] rawBytes() {
            return raw.toByteArray();
        }

        synchronized byte[] realtimeBytes() {
            return realtime.toByteArray();
        }

        private void appendWithLimit(ByteArrayOutputStream target, byte[] bytes, int maxBytes) {
            if (bytes == null || bytes.length == 0 || target.size() >= maxBytes) {
                return;
            }
            int writable = Math.min(bytes.length, maxBytes - target.size());
            target.write(bytes, 0, writable);
        }
    }
}
