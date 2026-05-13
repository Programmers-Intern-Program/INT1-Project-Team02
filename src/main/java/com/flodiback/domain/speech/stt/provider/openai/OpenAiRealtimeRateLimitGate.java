package com.flodiback.domain.speech.stt.provider.openai;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OpenAiRealtimeRateLimitGate {
    private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|h|m|s)");
    private static final long DEFAULT_COOLDOWN_MS = Duration.ofSeconds(30).toMillis();
    private static final long COOLDOWN_PADDING_MS = 500L;

    private final AtomicLong blockedUntilEpochMs = new AtomicLong();
    private final LongSupplier currentTimeMillis;

    OpenAiRealtimeRateLimitGate() {
        this(System::currentTimeMillis);
    }

    OpenAiRealtimeRateLimitGate(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
    }

    boolean isBlocked() {
        return remainingMillis() > 0;
    }

    long remainingMillis() {
        return Math.max(0L, blockedUntilEpochMs.get() - currentTimeMillis.getAsLong());
    }

    void recordIfRateLimited(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || !message.toLowerCase(Locale.ROOT).contains("rate limit")) {
            return;
        }

        long retryAfterMs = parseRetryAfterMillis(message);
        long nextBlockedUntil = currentTimeMillis.getAsLong() + retryAfterMs + COOLDOWN_PADDING_MS;
        blockedUntilEpochMs.accumulateAndGet(nextBlockedUntil, Math::max);
    }

    IllegalStateException blockedException(String sessionId) {
        return new IllegalStateException("OpenAI Realtime rate limit cooldown is active. sessionId="
                + sessionId
                + ", retryAfterMs="
                + remainingMillis());
    }

    private long parseRetryAfterMillis(String message) {
        int retryIndex = message.toLowerCase(Locale.ROOT).indexOf("try again in");
        if (retryIndex < 0) {
            return DEFAULT_COOLDOWN_MS;
        }

        Matcher matcher = RETRY_AFTER_PATTERN.matcher(message.substring(retryIndex));
        long totalMillis = 0L;
        while (matcher.find()) {
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            totalMillis += switch (unit) {
                case "ms" -> Math.round(value);
                case "s" -> Math.round(value * 1_000L);
                case "m" -> Math.round(value * 60_000L);
                case "h" -> Math.round(value * 3_600_000L);
                default -> 0L;
            };
        }
        return totalMillis > 0 ? totalMillis : DEFAULT_COOLDOWN_MS;
    }
}
