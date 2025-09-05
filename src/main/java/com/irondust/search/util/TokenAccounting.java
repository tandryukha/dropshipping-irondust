package com.irondust.search.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe process-wide accounting for OpenAI token usage during an ingest run.
 *
 * <p>Usage:
 * - Call {@link #reset()} at the start of an ingest run.
 * - Instrument model calls to record usage via {@link #recordChatCompletionUsage(String, long, long, long)}
 *   and {@link #recordEmbeddingUsage(String, long)}.
 * - After the run, call {@link #snapshotWithCosts()} to obtain per-model totals and approximate costs.
 *
 * <p>Costs are approximate and derived from built-in defaults with environment-variable overrides.
 * For overrides, define:
 *   OPENAI_COST_<MODEL>_INPUT_PER_1K and OPENAI_COST_<MODEL>_OUTPUT_PER_1K (chat models)
 *   OPENAI_COST_<MODEL>_EMBED_PER_1K (embedding models)
 * Where <MODEL> is the uppercased model name with non-alphanumeric characters replaced by '_'.
 */
public final class TokenAccounting {
    private TokenAccounting() {}

    public static final class ModelUsage {
        public final AtomicLong promptTokens = new AtomicLong();
        public final AtomicLong completionTokens = new AtomicLong();
        public final AtomicLong totalTokens = new AtomicLong();
    }

    public static final class UsageWithCost {
        public final String model;
        public final long promptTokens;
        public final long completionTokens;
        public final long totalTokens;
        public final double costUsd;

        public UsageWithCost(String model, long promptTokens, long completionTokens, long totalTokens, double costUsd) {
            this.model = model;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
            this.costUsd = costUsd;
        }
    }

    private static final ConcurrentHashMap<String, ModelUsage> USAGE = new ConcurrentHashMap<>();

    public static void reset() {
        USAGE.clear();
    }

    public static void recordChatCompletionUsage(String model, long prompt, long completion, long total) {
        if (model == null || model.isBlank()) model = "unknown";
        ModelUsage mu = USAGE.computeIfAbsent(model, m -> new ModelUsage());
        if (prompt > 0) mu.promptTokens.addAndGet(prompt);
        if (completion > 0) mu.completionTokens.addAndGet(completion);
        if (total > 0) mu.totalTokens.addAndGet(total);
    }

    public static void recordEmbeddingUsage(String model, long promptTokens) {
        if (model == null || model.isBlank()) model = "unknown";
        ModelUsage mu = USAGE.computeIfAbsent(model, m -> new ModelUsage());
        if (promptTokens > 0) {
            mu.promptTokens.addAndGet(promptTokens);
            mu.totalTokens.addAndGet(promptTokens);
        }
    }

    public static Map<String, UsageWithCost> snapshotWithCosts() {
        Map<String, UsageWithCost> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, ModelUsage> e : USAGE.entrySet()) {
            String model = e.getKey();
            ModelUsage mu = e.getValue();
            long p = mu.promptTokens.get();
            long c = mu.completionTokens.get();
            long t = mu.totalTokens.get();
            double cost = estimateCostUsd(model, p, c, t);
            out.put(model, new UsageWithCost(model, p, c, t, roundMoney(cost)));
        }
        return out;
    }

    public static double totalCostUsd(Map<String, UsageWithCost> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return 0.0;
        double sum = 0.0;
        for (UsageWithCost u : snapshot.values()) sum += u.costUsd;
        return roundMoney(sum);
    }

    private static double estimateCostUsd(String model, long promptTokens, long completionTokens, long totalTokens) {
        String key = toEnvKey(model);
        Double inPer1k = getEnvDouble("OPENAI_COST_" + key + "_INPUT_PER_1K");
        Double outPer1k = getEnvDouble("OPENAI_COST_" + key + "_OUTPUT_PER_1K");
        Double embPer1k = getEnvDouble("OPENAI_COST_" + key + "_EMBED_PER_1K");

        // Defaults (approximate; override via env to keep up-to-date):
        // NOTE: All defaults below are expressed as cost per 1K tokens to match the
        // PER_1K env var semantics. Official OpenAI prices are per 1M tokens, so we
        // convert them here (divide by 1000).
        // gpt-4o-mini: $0.15 / 1M input, $0.60 / 1M output => per 1K: 0.00015 / 0.00060
        if (inPer1k == null && outPer1k == null && embPer1k == null) {
            String m = model.toLowerCase();
            if (m.contains("gpt-4o-mini")) {
                inPer1k = 0.00015; outPer1k = 0.00060;
            } else if (m.contains("gpt-4o")) {
                // gpt-4o: $5.00 / 1M input, $15.00 / 1M output => per 1K: 0.005 / 0.015
                // Please override with env if your account has different pricing.
                inPer1k = 0.005; outPer1k = 0.015;
            } else if (m.contains("text-embedding-3-large")) {
                // text-embedding-3-large: $0.13 / 1M => per 1K: 0.00013
                embPer1k = 0.00013;
            } else if (m.contains("text-embedding-3-small")) {
                // text-embedding-3-small: $0.02 / 1M => per 1K: 0.00002
                embPer1k = 0.00002;
            } else {
                // Unknown model: zero unless overridden by env
                inPer1k = 0.0; outPer1k = 0.0; embPer1k = 0.0;
            }
        }

        double cost = 0.0;
        if (embPer1k != null) {
            cost += (promptTokens / 1000.0) * embPer1k;
        } else {
            if (inPer1k != null) cost += (promptTokens / 1000.0) * inPer1k;
            if (outPer1k != null) cost += (completionTokens / 1000.0) * outPer1k;
        }
        return cost;
    }

    private static String toEnvKey(String model) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < model.length(); i++) {
            char ch = model.charAt(i);
            if (Character.isLetterOrDigit(ch)) sb.append(Character.toUpperCase(ch));
            else sb.append('_');
        }
        return sb.toString();
    }

    private static Double getEnvDouble(String name) {
        try {
            String v = System.getenv(name);
            if (v == null || v.isBlank()) return null;
            return Double.parseDouble(v);
        } catch (Exception ignored) { return null; }
    }

    private static double roundMoney(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}


