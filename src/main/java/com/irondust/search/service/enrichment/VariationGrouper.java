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
        
        // Remove common flavor/size suffixes
        String normalized = name.toLowerCase();
        
        // Remove flavor indicators
        normalized = normalized.replaceAll("\\s*(unflavored|flavored|citrus|berry|coffee|chocolate|vanilla)\\s*$", "");
        
        // Remove size indicators (keep the main product name)
        normalized = normalized.replaceAll("\\s*(\\d+\\s*(g|ml|kg|l|servings?|capsules?|tablets?))\\s*$", "");
        
        // Remove common suffixes
        normalized = normalized.replaceAll("\\s*(powder|capsules?|tablets?|drink|gel|bar)\\s*$", "");
        
        return normalized.trim();
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
