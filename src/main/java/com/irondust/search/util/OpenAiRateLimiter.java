package com.irondust.search.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Process-wide, lightweight OpenAI rate limiter.
 *
 * <p>Controls request-per-minute (RPM) and tokens-per-minute (TPM) budgets
 * using a simple synchronized minute window. Intended to be used as a coarse
 * throttle across all OpenAI calls (translations, enrichment, embeddings).
 *
 * <p>Configuration via environment variables (override defaults as needed):
 * - OPENAI_RPM (default 3000)
 * - OPENAI_TPM (default 200000)
 * - OPENAI_MIN_SLEEP_MS (default 10) — minimum sleep granularity when waiting
 *
 * <p>Usage:
 * - Call {@link #acquire(long)} before making an OpenAI request. Provide an
 *   estimated token count for prompt+completion (use a conservative estimate).
 * - The call blocks until there is budget in the current minute window.
 *
 * <p>Notes:
 * - This is intentionally simple and single-node. For multi-node deployments,
 *   use a distributed rate limiter.
 */
public final class OpenAiRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(OpenAiRateLimiter.class);

    private static final Object LOCK = new Object();
    private static final int DEFAULT_RPM = getEnvInt("OPENAI_RPM", 500);
    private static final long DEFAULT_TPM = getEnvLong("OPENAI_TPM", 200_000L);
    private static final long MIN_SLEEP_MS = Math.max(1L, getEnvLong("OPENAI_MIN_SLEEP_MS", 10L));

    private static volatile long windowStartMs = alignToMinute(System.currentTimeMillis());
    private static volatile int requestsUsed = 0;
    private static volatile long tokensUsed = 0L;

    private OpenAiRateLimiter() {}

    /**
     * Block until there is enough RPM/TPM budget to start the request, then
     * reserve the budget for this request.
     *
     * @param estimatedTokens conservative estimate for prompt + completion tokens
     */
    public static void acquire(long estimatedTokens) {
        long needTokens = Math.max(1L, estimatedTokens);
        int rpm = DEFAULT_RPM;
        long tpm = DEFAULT_TPM;

        while (true) {
            long now = System.currentTimeMillis();
            long minuteStart = alignToMinute(now);
            long sleepMs = 0L;

            synchronized (LOCK) {
                if (minuteStart > windowStartMs) {
                    windowStartMs = minuteStart;
                    requestsUsed = 0;
                    tokensUsed = 0L;
                }

                boolean haveRequest = (requestsUsed + 1) <= rpm;
                boolean haveTokens = (tokensUsed + needTokens) <= tpm;
                if (haveRequest && haveTokens) {
                    requestsUsed += 1;
                    tokensUsed += needTokens;
                    return; // reserved, proceed
                }

                long nextWindowStart = windowStartMs + TimeUnit.MINUTES.toMillis(1);
                sleepMs = Math.max(1L, nextWindowStart - now);
            }

            try {
                // Sleep outside synchronized block to avoid blocking other threads
                long chunk = Math.max(MIN_SLEEP_MS, Math.min(250L, sleepMs));
                Thread.sleep(chunk);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Approximate tokens from text length (~4 chars per token). */
    public static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0L;
        return Math.max(1L, Math.round(text.length() / 4.0));
    }

    /** Optional hook when a 429 occurs, to log and slightly nudge pacing. */
    public static void onRateLimitHit() {
        // Nudge: brief sleep to avoid immediate retry stampedes
        try { Thread.sleep(Math.max(MIN_SLEEP_MS, 50L)); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static long alignToMinute(long epochMs) {
        long minute = TimeUnit.MILLISECONDS.toMinutes(epochMs);
        return TimeUnit.MINUTES.toMillis(minute);
    }

    private static int getEnvInt(String name, int def) {
        try {
            String v = System.getenv(name);
            if (v == null || v.isBlank()) return def;
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long getEnvLong(String name, long def) {
        try {
            String v = System.getenv(name);
            if (v == null || v.isBlank()) return def;
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return def;
        }
    }
}


