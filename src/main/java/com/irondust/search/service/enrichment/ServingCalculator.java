package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;

public class ServingCalculator implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    @Override
    public boolean supports(RawProduct raw) {
        return true; // ServingCalculator supports all products
    }

    @Override
    public EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar) {
        Map<String, Object> updates = new HashMap<>();
        Map<String, Double> confidence = new HashMap<>();
        Map<String, String> sources = new HashMap<>();

        // If servings is missing (and no range present) but we have net_weight_g and serving_size_g, calculate it
        boolean hasRange = (soFar.getServings_min() != null || soFar.getServings_max() != null);
        if (!hasRange && soFar.getServings() == null && soFar.getNet_weight_g() != null && soFar.getServing_size_g() != null) {
            double servings = soFar.getNet_weight_g() / soFar.getServing_size_g();
            if (servings > 0 && servings <= 1000) { // Sanity check
                updates.put("servings", (int) Math.round(servings));
                confidence.put("servings", 0.85);
                sources.put("servings", "derived");
            } else {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "servings", 
                    String.format("Calculated servings %.2f seems unreasonable", servings)));
            }
        }

        return new EnrichmentDelta(updates, confidence, sources, null);
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
