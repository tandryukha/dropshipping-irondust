package com.irondust.search.util;

/**
 * Utilities for cleaning up product titles for display.
 *
 * <p>Sanitization is intentionally conservative: it trims whitespace, normalizes
 * non‑breaking spaces, and removes only obvious leading/trailing separator
 * garbage such as standalone dashes ("-", "–", "—"), pipes, or colons.
 * The core content in the middle of the string is not altered.
 */
public final class TitleUtils {
    private TitleUtils() {}

    /**
     * Returns a cleaned version of the given title string.
     *
     * <p>Rules applied in order:
     * <ol>
     *   <li>Convert non-breaking spaces to regular spaces</li>
     *   <li>Collapse repeated whitespace</li>
     *   <li>Trim leading/trailing whitespace</li>
     *   <li>Strip leading/trailing separator garbage (dashes, pipes, colons, bullets)</li>
     * </ol>
     *
     * <p>If the result becomes empty, returns a trimmed original as a fallback.
     */
    public static String sanitizeTitle(String input) {
        if (input == null) return null;
        String s = input;
        // Normalize spaces
        s = s.replace('\u00A0', ' ');
        s = s.replaceAll("\\s+", " ").trim();

        // Remove obvious leading/trailing separators and surrounding spaces.
        // Run a couple of passes to catch mixed sequences like "— |".
        for (int i = 0; i < 2; i++) {
            s = s.replaceAll("^(?:[\\s]*[\\-–—|:;·•]+[\\s]*)+", "");
            s = s.replaceAll("(?:[\\s]*[\\-–—|:;·•]+[\\s]*)+$", "");
        }

        // Deduplicate accidental repeated last word (e.g., "Unflavored unflavored")
        // Case-insensitive, only at the very end to avoid harming valid names.
        s = s.replaceAll("(?i)(\\b[\\p{L}0-9\\u00C0-\\u024F\\u0100-\\u017F]+\\b)\\s+\\1$", "$1");

        s = s.trim();
        return s.isEmpty() ? input.trim() : s;
    }
}


