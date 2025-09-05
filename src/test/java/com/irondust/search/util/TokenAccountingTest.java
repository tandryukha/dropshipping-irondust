package com.irondust.search.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link TokenAccounting} cost estimation defaults.
 * Ensures defaults use per-1K token prices converted from OpenAI per-1M pricing.
 */
class TokenAccountingTest {

    @AfterEach
    void cleanUp() {
        TokenAccounting.reset();
    }

    @Test
    void gpt4oMini_pricing_is_per_1k_converted_from_per_1m() {
        // Given: usage matching the user's report
        long prompt = 3_277_125L;     // 3.277M
        long completion = 1_981_907L; // 1.982M
        long total = prompt + completion;

        TokenAccounting.reset();
        TokenAccounting.recordChatCompletionUsage("gpt-4o-mini-2024-07-18", prompt, completion, total);

        Map<String, TokenAccounting.UsageWithCost> snap = TokenAccounting.snapshotWithCosts();
        TokenAccounting.UsageWithCost usage = snap.get("gpt-4o-mini-2024-07-18");
        assertNotNull(usage);

        // expected: (prompt/1k)*0.00015 + (completion/1k)*0.00060, rounded to cents
        double expected = Math.round(((prompt / 1000.0) * 0.00015 + (completion / 1000.0) * 0.00060) * 100.0) / 100.0;
        assertEquals(expected, usage.costUsd, 0.0001, "Cost should match per-1K converted pricing");
        assertEquals(1.68, usage.costUsd, 0.01, "Should be about $1.68 for the given usage");
    }

    @Test
    void gpt4o_pricing_is_per_1k_converted_from_per_1m() {
        // Given: 100k input + 100k output => expected cost: (100 * 0.005) + (100 * 0.015) = 2.0
        long prompt = 100_000L;
        long completion = 100_000L;
        long total = prompt + completion;

        TokenAccounting.reset();
        TokenAccounting.recordChatCompletionUsage("gpt-4o-2024-05-13", prompt, completion, total);

        Map<String, TokenAccounting.UsageWithCost> snap = TokenAccounting.snapshotWithCosts();
        TokenAccounting.UsageWithCost usage = snap.get("gpt-4o-2024-05-13");
        assertNotNull(usage);
        assertEquals(2.00, usage.costUsd, 0.001);
    }
}


