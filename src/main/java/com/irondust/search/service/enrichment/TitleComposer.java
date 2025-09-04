package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;

/**
 * Builds a human-friendly display title while keeping the canonical Woo name intact.
 *
 * <p>Rules (deterministic, conservative):
 * <ul>
 *   <li>Remove brand prefix from the original name when the brand appears at the very start.</li>
 *   <li>Compose: <code>{baseName} — {Brand}</code> when a brand is known.</li>
 *   <li>Collapse extra whitespace and common separators.</li>
 * </ul>
 *
 * <p>The step intentionally avoids aggressive rewriting (keeps variant tokens, series, etc.).
 * It does not change the canonical <code>name</code> field; instead, it populates
 * <code>display_title</code> for UI lists, carousels, and alternatives.</li>
 */
public class TitleComposer implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    @Override
    public boolean supports(RawProduct raw) {
        return raw != null && raw.getName() != null && !raw.getName().isBlank();
    }

    @Override
    public EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar) {
        String name = raw.getName();
        String brandName = (raw.getBrand_name() != null && !raw.getBrand_name().isBlank())
                ? raw.getBrand_name().trim()
                : null;
        String brandSlug = raw.getBrand_slug();

        String base = stripLeadingBrand(name, brandName, brandSlug);
        base = normalizeSpacesAndDashes(base);
        String display;
        String brandForSuffix = (brandName != null && !brandName.isBlank()) ? brandName : brandSlug;
        if (brandForSuffix != null && !brandForSuffix.isBlank()) {
            display = base + " — " + brandForSuffix.trim();
        } else {
            display = base;
        }
        display = display.trim();
        if (display.isBlank()) {
            return new EnrichmentDelta(Map.of(), Map.of(), Map.of(), null);
        }

        Map<String, Object> updates = new HashMap<>();
        Map<String, Double> confidence = new HashMap<>();
        Map<String, String> sources = new HashMap<>();
        updates.put("display_title", display);
        confidence.put("display_title", 0.7);
        sources.put("display_title", "compose");
        return new EnrichmentDelta(updates, confidence, sources, null);
    }

    private static String stripLeadingBrand(String name, String brandName, String brandSlug) {
        if (name == null) return null;
        String result = name.trim();
        // Build alias set: full brand name, first word, acronym, slug and slug's first token
        java.util.LinkedHashSet<String> aliases = new java.util.LinkedHashSet<>();
        if (brandName != null && !brandName.isBlank()) {
            String cleaned = brandName.replaceAll("[®™]", "").trim();
            if (!cleaned.isBlank()) aliases.add(cleaned);
            String firstWord = cleaned.split("\\s+")[0];
            if (firstWord != null && !firstWord.isBlank()) aliases.add(firstWord);
            String acronym = cleaned.chars()
                    .mapToObj(c -> (char) c)
                    .filter(ch -> Character.isUpperCase(ch))
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                    .toString();
            if (acronym.length() >= 2) aliases.add(acronym);
            String compact = cleaned.replaceAll("[^A-Za-z0-9]", "");
            if (!compact.isBlank()) aliases.add(compact);
        }
        if (brandSlug != null && !brandSlug.isBlank()) {
            aliases.add(brandSlug);
            String firstSlug = brandSlug.contains("-") ? brandSlug.substring(0, brandSlug.indexOf('-')) : brandSlug;
            if (firstSlug != null && !firstSlug.isBlank()) aliases.add(firstSlug);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (String alias : aliases) {
                String aliasPattern = java.util.regex.Pattern.quote(alias).replace("\\ ", "\\s+");
                String pattern = "^(?i)\\s*(" + aliasPattern + ")([\\u00A0\\s]*[:;\\-–—|]*\\s*)";
                String stripped = result.replaceFirst(pattern, "").trim();
                if (!stripped.equals(result)) {
                    result = stripped;
                    changed = true;
                    break; // try again from start in case of stacked tokens
                }
            }
        }
        return result;
    }

    private static String normalizeSpacesAndDashes(String s) {
        if (s == null) return null;
        String out = s.replace('\u00A0', ' ');
        out = out.replaceAll("\\s+", " ");
        out = out.replaceAll("\\s*([\\-–—])\\s*", "$1");
        return out.trim();
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}


