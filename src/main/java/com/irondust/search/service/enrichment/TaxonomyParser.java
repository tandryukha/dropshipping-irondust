package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;
import java.util.regex.Pattern;

public class TaxonomyParser implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    // Goal tags mapping from categories/keywords
    private static final Map<String, List<String>> GOAL_MAPPINGS = Map.of(
        "preworkout", Arrays.asList("preworkout", "enne treeningut", "до тренировки"),
        "strength", Arrays.asList("strength", "jõud", "сила", "muscle", "lihas"),
        "endurance", Arrays.asList("endurance", "vastupidavus", "выносливость", "stamina"),
        "lean_muscle", Arrays.asList("lean", "lean muscle", "lean lihas", "похудение"),
        "recovery", Arrays.asList("recovery", "taastumine", "восстановление", "post-workout"),
        "weight_loss", Arrays.asList("weight loss", "kaalulangus", "похудение", "fat burn"),
        "wellness", Arrays.asList("wellness", "tervis", "здоровье", "vitamin", "vitamiin")
    );

    // Diet tags patterns
    private static final Pattern VEGAN_PATTERN = Pattern.compile(
        "vegan|veganii|веган", Pattern.CASE_INSENSITIVE);
    private static final Pattern GLUTEN_FREE_PATTERN = Pattern.compile(
        "gluteenivaba|gluten.?free|без глютена", Pattern.CASE_INSENSITIVE);
    private static final Pattern LACTOSE_FREE_PATTERN = Pattern.compile(
        "laktoosivaba|lactose.?free|без лактозы", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUGAR_FREE_PATTERN = Pattern.compile(
        "sugar.?free|без сахара|suhkruvaba", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(RawProduct raw) {
        return true; // TaxonomyParser supports all products
    }

    @Override
    public EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar) {
        Map<String, Object> updates = new HashMap<>();
        Map<String, Double> confidence = new HashMap<>();
        Map<String, String> sources = new HashMap<>();

        // Parse goal tags
        List<String> goalTags = parseGoalTags(raw);
        if (!goalTags.isEmpty()) {
            updates.put("goal_tags", goalTags);
            confidence.put("goal_tags", 0.8);
            sources.put("goal_tags", "taxonomy");
        }

        // Parse diet tags
        List<String> dietTags = parseDietTags(raw);
        if (!dietTags.isEmpty()) {
            updates.put("diet_tags", dietTags);
            confidence.put("diet_tags", 0.9);
            sources.put("diet_tags", "taxonomy");
        }

        return new EnrichmentDelta(updates, confidence, sources, null);
    }

    private List<String> parseGoalTags(RawProduct raw) {
        Set<String> goals = new HashSet<>();
        String searchText = raw.getSearch_text().toLowerCase();

        // Check categories
        if (raw.getCategories_names() != null) {
            for (String category : raw.getCategories_names()) {
                String catLower = category.toLowerCase();
                for (Map.Entry<String, List<String>> entry : GOAL_MAPPINGS.entrySet()) {
                    for (String keyword : entry.getValue()) {
                        if (catLower.contains(keyword)) {
                            goals.add(entry.getKey());
                            break;
                        }
                    }
                }
            }
        }

        // Check search text
        for (Map.Entry<String, List<String>> entry : GOAL_MAPPINGS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (searchText.contains(keyword)) {
                    goals.add(entry.getKey());
                    break;
                }
            }
        }

        return new ArrayList<>(goals);
    }

    private List<String> parseDietTags(RawProduct raw) {
        Set<String> diets = new HashSet<>();
        String searchText = raw.getSearch_text();

        // Check attributes first
        if (raw.getDynamic_attrs() != null) {
            List<String> veganAttrs = raw.getDynamic_attrs().get("attr_pa_kas-see-on-veganisobralik");
            if (veganAttrs != null && !veganAttrs.isEmpty()) {
                if (veganAttrs.get(0).equals("jah") || veganAttrs.get(0).equals("yes")) {
                    diets.add("vegan");
                }
            }
        }

        // Check text patterns
        if (VEGAN_PATTERN.matcher(searchText).find()) {
            diets.add("vegan");
        }
        if (GLUTEN_FREE_PATTERN.matcher(searchText).find()) {
            diets.add("gluten_free");
        }
        if (LACTOSE_FREE_PATTERN.matcher(searchText).find()) {
            diets.add("lactose_free");
        }
        if (SUGAR_FREE_PATTERN.matcher(searchText).find()) {
            diets.add("sugar_free");
        }

        return new ArrayList<>(diets);
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
