package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;

public class PriceCalculator implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    @Override
    public boolean supports(RawProduct raw) {
        return true; // PriceCalculator supports all products
    }

    @Override
    public EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar) {
        Map<String, Object> updates = new HashMap<>();
        Map<String, Double> confidence = new HashMap<>();
        Map<String, String> sources = new HashMap<>();

        // Calculate price in euros
        if (raw.getPrice_cents() != null) {
            double price = raw.getPrice_cents() / 100.0;
            updates.put("price", price);
            confidence.put("price", 1.0);
            sources.put("price", "derived");
        }

        // Calculate price per serving
        if (soFar.getPrice() != null && soFar.getServings() != null && soFar.getServings() > 0) {
            double pricePerServing = soFar.getPrice() / soFar.getServings();
            updates.put("price_per_serving", Math.round(pricePerServing * 100.0) / 100.0);
            confidence.put("price_per_serving", 0.95);
            sources.put("price_per_serving", "derived");
        }

        // Calculate price per 100g (for powders)
        if (soFar.getPrice() != null && soFar.getNet_weight_g() != null && soFar.getNet_weight_g() > 0) {
            double pricePer100g = (soFar.getPrice() * 100) / soFar.getNet_weight_g();
            updates.put("price_per_100g", Math.round(pricePer100g * 100.0) / 100.0);
            confidence.put("price_per_100g", 0.95);
            sources.put("price_per_100g", "derived");
        }

        return new EnrichmentDelta(updates, confidence, sources, null);
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
