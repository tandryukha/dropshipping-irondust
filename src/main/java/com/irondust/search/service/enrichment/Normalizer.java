package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;

public class Normalizer implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    // Heuristic detection patterns
    private static final java.util.regex.Pattern GRAMS_PATTERN = java.util.regex.Pattern.compile("\\b\\d{2,4}\\s?(g|gramm)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern CAPS_TOKENS = java.util.regex.Pattern.compile("\\b(caps|capsule|vcaps|kaps|kapslid|tabletid|tablet|tabs)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern TABS_TOKENS = java.util.regex.Pattern.compile("\\b(tabs?|tabletid|tablet)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern PULBER_TOKEN = java.util.regex.Pattern.compile("pulber", java.util.regex.Pattern.CASE_INSENSITIVE);

    // Form mappings from Estonian (and common English) slugs to canonical forms
    private static final Map<String, String> FORM_MAP = Map.ofEntries(
        Map.entry("pulber", "powder"),
        Map.entry("powder", "powder"),
        Map.entry("kapslid", "capsules"),
        Map.entry("kapsel", "capsules"),
        Map.entry("capsules", "capsules"),
        Map.entry("capsule", "capsules"),
        Map.entry("caps", "capsules"),
        Map.entry("tabletid", "tabs"),
        Map.entry("pehmekapslid", "capsules"),
        Map.entry("tablet", "tabs"),
        Map.entry("tablets", "tabs"),
        Map.entry("tabs", "tabs"),
        Map.entry("jook", "drink"),
        Map.entry("drink", "drink"),
        Map.entry("geel", "gel"),
        Map.entry("gel", "gel"),
        Map.entry("baar", "bar"),
        Map.entry("bar", "bar")
    );

    // Flavor mappings from Estonian slugs to canonical flavors
    private static final Map<String, String> FLAVOR_MAP = Map.of(
        "ei-mingit-maitset", "unflavored",
        "maitse", "flavored",
        "tsitrus", "citrus",
        "marja", "berry",
        "kohv", "coffee",
        "sokolaad", "chocolate",
        "vaanil", "vanilla"
    );

    // Boolean mappings
    private static final Map<String, Boolean> BOOLEAN_MAP = Map.of(
        "jah", true,
        "ei", false,
        "yes", true,
        "no", false
    );

    @Override
    public boolean supports(RawProduct raw) {
        return true; // Normalizer supports all products
    }

    @Override
    public EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar) {
        Map<String, Object> updates = new HashMap<>();
        Map<String, Double> confidence = new HashMap<>();
        Map<String, String> sources = new HashMap<>();

        // Normalize form from attributes
        String form = normalizeForm(raw);
        if (form != null) {
            updates.put("form", form);
            confidence.put("form", 0.95);
            sources.put("form", "attribute");
        } else {
            // Heuristic fallback when attributes missing and evidence is unambiguous
            String hForm = inferFormHeuristic(raw);
            if (hForm != null) {
                updates.put("form", hForm);
                confidence.put("form", 0.6);
                sources.put("form", "heuristic");
            }
        }

        // Normalize flavor from attributes
        String flavor = normalizeFlavor(raw);
        if (flavor != null) {
            updates.put("flavor", flavor);
            confidence.put("flavor", 0.95);
            sources.put("flavor", "attribute");
        }

        // Normalize decimal numbers (comma to dot)
        normalizeDecimals(raw, updates, confidence, sources);

        return new EnrichmentDelta(updates, confidence, sources, null);
    }

    private String inferFormHeuristic(RawProduct raw) {
        StringBuilder sb = new StringBuilder();
        if (raw.getName() != null) sb.append(raw.getName()).append(' ');
        if (raw.getSlug() != null) sb.append(raw.getSlug()).append(' ');
        if (raw.getSearch_text() != null) sb.append(raw.getSearch_text());
        String text = sb.toString().toLowerCase();

        boolean mentionsCaps = CAPS_TOKENS.matcher(text).find();
        boolean mentionsTabs = TABS_TOKENS.matcher(text).find();
        boolean mentionsPulberWord = PULBER_TOKEN.matcher(text).find();
        boolean hasGramsInText = GRAMS_PATTERN.matcher(text).find();

        boolean categorySuggestsPowder = false;
        if (raw.getCategories_names() != null) {
            for (String c : raw.getCategories_names()) {
                String cl = c.toLowerCase();
                if (cl.contains("kreatiin") || cl.contains("monohüdraat") || cl.contains("monohudraat")
                    || cl.contains("üksikud aminohapped") || cl.contains("uksikud aminohapped")) {
                    categorySuggestsPowder = true;
                    break;
                }
            }
        }

        // Evidence aggregation
        boolean capsEvidence = mentionsCaps;
        boolean powderEvidence = mentionsPulberWord || (categorySuggestsPowder && hasGramsInText) || (!capsEvidence && hasGramsInText);

        // Prefer capsule/tablet if explicitly mentioned
        if (capsEvidence && !powderEvidence) {
            return mentionsTabs ? "tabs" : "capsules";
        }
        // If grams present and no capsule/tablet signal, assume powder
        if (powderEvidence && !capsEvidence) {
            return "powder";
        }
        return null;
    }

    private String normalizeForm(RawProduct raw) {
        if (raw.getDynamic_attrs() == null) return null;

        // Primary expected key
        List<String> formAttrs = raw.getDynamic_attrs().get("attr_pa_valjalaske-vorm");
        if (formAttrs == null || formAttrs.isEmpty()) {
            // Fallback: find any attribute whose taxonomy key mentions "vorm"
            for (Map.Entry<String, java.util.List<String>> e : raw.getDynamic_attrs().entrySet()) {
                String k = e.getKey();
                if (k != null && k.contains("vorm")) {
                    formAttrs = e.getValue();
                    if (formAttrs != null && !formAttrs.isEmpty()) break;
                }
            }
        }

        if (formAttrs != null && !formAttrs.isEmpty()) {
            String slug = formAttrs.get(0);
            String canonical = FORM_MAP.get(slug);
            if (canonical != null) return canonical;
            // Try stripping locale suffixes like -et, -ru, -en
            String stripped = stripLocaleSuffix(slug);
            canonical = FORM_MAP.get(stripped);
            if (canonical != null) return canonical;
            // Heuristic fallback by substring
            String s = stripped != null ? stripped : slug;
            String sl = s.toLowerCase();
            if (sl.contains("kaps")) return "capsules";
            if (sl.contains("tablet")) return "tabs";
            if (sl.contains("pulber") || sl.contains("powder")) return "powder";
            if (sl.contains("jook") || sl.contains("drink")) return "drink";
            if (sl.contains("geel") || sl.contains("gel")) return "gel";
            if (sl.contains("baar") || sl.contains("bar")) return "bar";
        }
        return null;
    }

    private String normalizeFlavor(RawProduct raw) {
        if (raw.getDynamic_attrs() == null) return null;
        
        List<String> flavorAttrs = raw.getDynamic_attrs().get("attr_pa_maitse");
        if (flavorAttrs != null && !flavorAttrs.isEmpty()) {
            String slug = flavorAttrs.get(0);
            String mapped = FLAVOR_MAP.get(slug);
            if (mapped != null) return mapped;
            String stripped = stripLocaleSuffix(slug);
            return FLAVOR_MAP.get(stripped);
        }
        return null;
    }

    private void normalizeDecimals(RawProduct raw, Map<String, Object> updates, 
                                 Map<String, Double> confidence, Map<String, String> sources) {
        // This would handle any decimal normalization if needed
        // For now, we'll rely on the UnitParser to handle this
    }

    private String stripLocaleSuffix(String slug) {
        if (slug == null) return null;
        // Remove trailing -et/-ru/-en if present
        if (slug.endsWith("-et") || slug.endsWith("-ru") || slug.endsWith("-en")) {
            return slug.substring(0, Math.max(0, slug.length() - 3));
        }
        return slug;
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
