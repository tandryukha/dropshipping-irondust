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

        // Pass through sale/regular cents if present (for indexing/debugging)
        if (raw.getRegular_price_cents() != null) {
            updates.put("regular_price_cents", raw.getRegular_price_cents());
            confidence.put("regular_price_cents", 1.0);
            sources.put("regular_price_cents", "raw");
        }
        if (raw.getSale_price_cents() != null) {
            updates.put("sale_price_cents", raw.getSale_price_cents());
            confidence.put("sale_price_cents", 1.0);
            sources.put("sale_price_cents", "raw");
        }

        // Calculate price in euros
        if (raw.getPrice_cents() != null) {
            double price = raw.getPrice_cents() / 100.0;
            updates.put("price", price);
            confidence.put("price", 1.0);
            sources.put("price", "derived");
        }

        // Discount percent if regular and sale present
        if (raw.getRegular_price_cents() != null && raw.getSale_price_cents() != null
                && raw.getRegular_price_cents() > 0 && raw.getSale_price_cents() < raw.getRegular_price_cents()) {
            double rp = raw.getRegular_price_cents() / 100.0;
            double sp = raw.getSale_price_cents() / 100.0;
            double pct = Math.round(((rp - sp) / rp) * 1000.0) / 10.0; // one decimal
            updates.put("discount_pct", pct);
            updates.put("is_on_sale", true);
            confidence.put("discount_pct", 1.0);
            confidence.put("is_on_sale", 1.0);
            sources.put("discount_pct", "derived");
            sources.put("is_on_sale", "derived");
        }

        // Use a reliable local price for all downstream calculations
        Double localPrice = soFar.getPrice();
        if (localPrice == null && raw.getPrice_cents() != null) {
            localPrice = raw.getPrice_cents() / 100.0;
        }

        // Calculate price per serving for exact servings
        if (localPrice != null && soFar.getServings() != null && soFar.getServings() > 0) {
            double pricePerServing = localPrice / soFar.getServings();
            updates.put("price_per_serving", Math.round(pricePerServing * 100.0) / 100.0);
            confidence.put("price_per_serving", 0.95);
            sources.put("price_per_serving", "derived");
        }
        // Calculate price per serving range when servings_min/max present
        if (localPrice != null && soFar.getServings_min() != null && soFar.getServings_max() != null
                && soFar.getServings_min() > 0 && soFar.getServings_max() > 0) {
            double p = localPrice;
            double min = Math.round((p / soFar.getServings_max()) * 100.0) / 100.0; // lower price per serving
            double max = Math.round((p / soFar.getServings_min()) * 100.0) / 100.0; // higher price per serving
            updates.put("price_per_serving_min", min);
            updates.put("price_per_serving_max", max);
            confidence.put("price_per_serving_min", 0.95);
            confidence.put("price_per_serving_max", 0.95);
            sources.put("price_per_serving_min", "derived");
            sources.put("price_per_serving_max", "derived");
        }

        // Calculate price per 100g for weight-based forms only (powder/drink/gel/bar)
        if (localPrice != null) {
            String form = soFar.getForm();
            boolean weightBased = "powder".equals(form) || "drink".equals(form) || "gel".equals(form) || "bar".equals(form) || form == null;
            if (weightBased) {
                Double weight = soFar.getNet_weight_g();
                Double ss = soFar.getServing_size_g();
                Integer s = soFar.getServings();
                Integer sMin = soFar.getServings_min();
                Integer sMax = soFar.getServings_max();
                Integer usedServings = s != null ? s : (sMax != null ? sMax : sMin);

                // Prefer derived weight if declared weight looks like a single serving or too small vs. expected
                if (ss != null && usedServings != null && usedServings > 0) {
                    double candidate = ss * usedServings;
                    if (weight == null || weight <= ss * 1.5 || weight < candidate * 0.6) {
                        weight = candidate;
                    }
                }

                if (weight != null && weight > 0) {
                    double pricePer100g = (localPrice * 100) / weight;
                    updates.put("price_per_100g", Math.round(pricePer100g * 100.0) / 100.0);
                    confidence.put("price_per_100g", 0.95);
                    sources.put("price_per_100g", "derived");
                }
            }
        }

        // Calculate price per unit for count-based forms (capsules/tabs)
        if (localPrice != null && soFar.getUnit_count() != null && soFar.getUnit_count() > 0) {
            double ppu = localPrice / soFar.getUnit_count();
            updates.put("price_per_unit", Math.round(ppu * 100.0) / 100.0);
            confidence.put("price_per_unit", 0.95);
            sources.put("price_per_unit", "derived");
        }

        return new EnrichmentDelta(updates, confidence, sources, null);
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
