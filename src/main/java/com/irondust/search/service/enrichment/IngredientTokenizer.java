package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;

/**
 * Extracts a conservative set of ingredient tokens only when explicitly present
 * in the text (no inference). The output feeds search recall and future vector
 * embeddings.
 */
public class IngredientTokenizer implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        "and", "or", "with", "from", "of", "the", "a", "an", "in", "to",
        "sisaldab", "koos", "ja", "või", "из", "и", "с", "для", "в"
    ));

    @Override
    public boolean supports(RawProduct raw) {
        return true;
    }

    @Override
    public EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar) {
        Map<String, Object> updates = new HashMap<>();
        Map<String, Double> confidence = new HashMap<>();
        Map<String, String> sources = new HashMap<>();

        List<String> tokens = extractExplicitIngredients(raw);
        if (!tokens.isEmpty()) {
            updates.put("ingredients_key", tokens);
            confidence.put("ingredients_key", 0.9);
            sources.put("ingredients_key", "regex");
        }

        return new EnrichmentDelta(updates, confidence, sources, null);
    }

    private List<String> extractExplicitIngredients(RawProduct raw) {
        String description = raw.getDescription();
        if (description == null || description.isBlank()) return List.of();

        // Look for an explicit Ingredients section in multiple languages
        // Examples: "Ingredients:", "Koostisosad:", "Состав:".
        String lower = description.toLowerCase();
        int idx = indexOfAny(lower, Arrays.asList("ingredients:", "koostisosad:", "состав:"));
        if (idx < 0) return List.of();

        String after = lower.substring(idx);
        // Cut at next markup block or sentence end heuristics
        int cut = indexOfAny(after, Arrays.asList("</", "<br", "\n\n", "\r\n\r\n", "</p>"));
        String segment = cut > 0 ? after.substring(0, cut) : after;

        // Split by commas/semicolons and normalize
        String[] parts = segment.replaceAll("[<>/\n]", " ").split("[;,]");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.replaceAll("[^a-zа-яõäöüšž\\- ]", " ")
                .replaceAll(",+", " ")
                .trim();
            if (t.isBlank()) continue;
            // Keep 1-3 word tokens that are not stopwords
            String[] words = t.split("\\s+");
            if (words.length > 3) continue;
            String head = words[0];
            if (STOPWORDS.contains(head)) continue;
            if (head.length() < 3) continue;
            out.add(t);
        }
        // Deduplicate while preserving order
        LinkedHashSet<String> dedup = new LinkedHashSet<>(out);
        return new ArrayList<>(dedup);
    }

    private int indexOfAny(String haystack, List<String> needles) {
        int best = -1;
        for (String n : needles) {
            int i = haystack.indexOf(n);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}


