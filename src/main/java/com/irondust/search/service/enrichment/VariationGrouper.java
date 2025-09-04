package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;

public class VariationGrouper implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    @Override
    public boolean supports(RawProduct raw) {
        return true; // VariationGrouper supports all products
    }

    @Override
    public EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar) {
        Map<String, Object> updates = new HashMap<>();
        Map<String, Double> confidence = new HashMap<>();
        Map<String, String> sources = new HashMap<>();

        // Generate parent_id based on normalized base title + brand
        String parentId = generateParentId(raw, soFar);
        if (parentId != null) {
            updates.put("parent_id", parentId);
            confidence.put("parent_id", 0.8);
            sources.put("parent_id", "heuristic");
        }

        // For now, set variant_group_id same as parent_id
        if (parentId != null) {
            updates.put("variant_group_id", parentId);
            confidence.put("variant_group_id", 0.8);
            sources.put("variant_group_id", "heuristic");
        }

        return new EnrichmentDelta(updates, confidence, sources, null);
    }

    private String generateParentId(RawProduct raw, ParsedProduct soFar) {
        if (raw.getName() == null || raw.getBrand_slug() == null) {
            return null;
        }

        // Create a normalized base title by removing flavor/size indicators
        String baseTitle = normalizeBaseTitle(raw.getName());
        
        // Combine brand + base title
        String parentId = raw.getBrand_slug() + "-" + baseTitle;
        
        // Clean up: lowercase, replace spaces with hyphens, remove special chars
        parentId = parentId.toLowerCase()
            .replaceAll("\\s+", "-")
            .replaceAll("[^a-z0-9-]", "")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        
        return parentId;
    }

    private String normalizeBaseTitle(String name) {
        if (name == null) return "";

        // Lowercase copy for normalization
        String normalized = name.toLowerCase();

        // Drop bracketed/parenthesized flavor/size parts at the end, e.g., "(raspberry)" or "[cherry-lime]"
        normalized = normalized.replaceAll("\\s*[\\(\uFF08\u3010\u005B][^\\)\uFF09\u3011\u005D]{1,40}[\\)\uFF09\u3011\u005D]\\s*$", "");

        // Remove common flavor words (keep base product). Include Estonian variants.
        normalized = normalized.replaceAll(
            "\\b(unflavored|flavored|vanilla|vanill|chocolate|sokolaad|šokolaad|s\u0161okolaad|cocoa|strawberry|maasikas|raspberry|vaarikas|berry|blueberry|mustikas|metsamarja|banana|banaan|banaanijogurt|jogurt|citrus|orange|apelsin|lemon|sidrun|lime|laim|cola|kola|cherry|kirss|apple|\u00F5un|oun|mango|peach|virsik|pear|pirn|coffee|kohv|mocha|caramel|karamell|coconut|kookos|tropical|troopiline)\\b",
            "");

        // Remove explicit Estonian 'maitse' flavor tails often seen after a dash
        normalized = normalized.replaceAll("\\s*-\\s*maitse.*$", "");

        // Remove size indicators (keep the main product name)
        normalized = normalized.replaceAll("\\b(\\d{1,4}\\s*(g|gramm|kg|ml|l))\\b", "");
        normalized = normalized.replaceAll("\\b(\\d{1,3}\\s*(servings?|portsjonid?|capsules?|kapslid|tablets?|tabletid|tabs))\\b", "");

        // Remove common form suffixes
        normalized = normalized.replaceAll("\\s*(powder|pulber|capsules?|kapslid|tablets?|tabletid|tabs|drink|jook|gel|geel|bar|batoon)\\s*$", "");

        // Collapse whitespace and separators
        normalized = normalized.replaceAll("[\\s\u00A0]+", " ").replaceAll("\\s*[-–—]+\\s*$", "");

        return normalized.trim();
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
