package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;
import com.irondust.search.model.EnrichedProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EnrichmentPipeline {
    private static final Logger log = LoggerFactory.getLogger(EnrichmentPipeline.class);

    private final List<EnricherStep> deterministicSteps;
    private final List<Warn> allWarnings = new ArrayList<>();

    public EnrichmentPipeline() {
        // Initialize deterministic enrichment steps in order
        this.deterministicSteps = Arrays.asList(
            new Normalizer(),
            new UnitParser(),
            new ServingCalculator(),
            new PriceCalculator(),
            new TaxonomyParser(),
            new VariationGrouper()
        );
    }

    public EnrichedProduct enrich(RawProduct raw) {
        log.info("Starting enrichment for product {}", raw.getId());
        
        // Start with a parsed product containing raw data
        ParsedProduct parsed = ParsedProduct.fromRawProduct(raw);
        
        // Apply deterministic enrichment steps
        for (EnricherStep step : deterministicSteps) {
            if (step.supports(raw)) {
                try {
                    EnrichmentDelta delta = step.apply(raw, parsed);
                    applyDelta(parsed, delta);
                    
                    // Collect warnings
                    allWarnings.addAll(step.getWarnings());
                    
                    log.debug("Applied {} to product {}, updates: {}", 
                        step.getName(), raw.getId(), delta.getUpdates().keySet());
                } catch (Exception e) {
                    log.error("Error applying {} to product {}: {}", 
                        step.getName(), raw.getId(), e.getMessage(), e);
                }
            }
        }

        // Convert to enriched product
        EnrichedProduct enriched = EnrichedProduct.fromParsedProduct(parsed);
        
        // Add warnings to the product
        if (!allWarnings.isEmpty()) {
            List<String> warningMessages = new ArrayList<>();
            for (Warn warn : allWarnings) {
                if (warn.getProductId().equals(raw.getId())) {
                    warningMessages.add(String.format("%s: %s", warn.getCode(), warn.getMessage()));
                }
            }
            enriched.setWarnings(warningMessages);
        }

        log.info("Completed enrichment for product {}, warnings: {}", 
            raw.getId(), enriched.getWarnings() != null ? enriched.getWarnings().size() : 0);
        
        return enriched;
    }

    private void applyDelta(ParsedProduct parsed, EnrichmentDelta delta) {
        if (delta.getUpdates() == null) return;

        for (Map.Entry<String, Object> entry : delta.getUpdates().entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            
            // Apply the update using reflection or switch statement
            switch (field) {
                case "form":
                    parsed.setForm((String) value);
                    break;
                case "flavor":
                    parsed.setFlavor((String) value);
                    break;
                case "net_weight_g":
                    parsed.setNet_weight_g((Double) value);
                    break;
                case "servings":
                    parsed.setServings((Integer) value);
                    break;
                case "serving_size_g":
                    parsed.setServing_size_g((Double) value);
                    break;
                case "price":
                    parsed.setPrice((Double) value);
                    break;
                case "price_per_serving":
                    parsed.setPrice_per_serving((Double) value);
                    break;
                case "price_per_100g":
                    parsed.setPrice_per_100g((Double) value);
                    break;
                case "goal_tags":
                    parsed.setGoal_tags((List<String>) value);
                    break;
                case "diet_tags":
                    parsed.setDiet_tags((List<String>) value);
                    break;
                case "ingredients_key":
                    parsed.setIngredients_key((List<String>) value);
                    break;
                case "parent_id":
                    parsed.setParent_id((String) value);
                    break;
                case "variant_group_id":
                    parsed.setVariant_group_id((String) value);
                    break;
                default:
                    log.warn("Unknown field in enrichment delta: {}", field);
            }
        }

        // Update provenance if available
        if (delta.getSources() != null && parsed.getProvenance() == null) {
            parsed.setProvenance(new HashMap<>());
        }
        if (delta.getSources() != null) {
            parsed.getProvenance().putAll(delta.getSources());
        }
    }

    public List<Warn> getAllWarnings() {
        return new ArrayList<>(allWarnings);
    }

    public void clearWarnings() {
        allWarnings.clear();
    }
}
