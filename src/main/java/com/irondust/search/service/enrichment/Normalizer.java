package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;

public class Normalizer implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    // Form mappings from Estonian slugs to canonical forms
    private static final Map<String, String> FORM_MAP = Map.of(
        "pulber", "powder",
        "kapslid", "capsules", 
        "tabletid", "tabs",
        "jook", "drink",
        "geel", "gel",
        "baar", "bar"
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

    private String normalizeForm(RawProduct raw) {
        if (raw.getDynamic_attrs() == null) return null;
        
        List<String> formAttrs = raw.getDynamic_attrs().get("attr_pa_valjalaske-vorm");
        if (formAttrs != null && !formAttrs.isEmpty()) {
            String slug = formAttrs.get(0);
            String canonical = FORM_MAP.get(slug);
            if (canonical != null) return canonical;
            // Try stripping locale suffixes like -et, -ru, -en
            String stripped = stripLocaleSuffix(slug);
            return FORM_MAP.get(stripped);
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
