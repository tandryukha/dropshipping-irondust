package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;
import com.irondust.search.model.EnrichedProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Orchestrates the product enrichment pipeline that transforms raw WooCommerce data
 * into structured, searchable product information.
 * 
 * <p>The enrichment pipeline follows a deterministic-first approach where all
 * possible parsing is done using rules and patterns before any AI processing.
 * This ensures consistency, explainability, and cost efficiency.
 * 
 * <h3>Pipeline Flow</h3>
 * <ol>
 *   <li><strong>RawProduct</strong> - Initial data from WooCommerce</li>
 *   <li><strong>Deterministic Parsing</strong> - Rule-based field extraction and normalization</li>
 *   <li><strong>ParsedProduct</strong> - Intermediate state with parsed fields</li>
 *   <li><strong>AI Enrichment</strong> - AI-generated content (Phase 2)</li>
 *   <li><strong>EnrichedProduct</strong> - Final data model for search</li>
 * </ol>
 * 
 * <h3>Deterministic Enrichment Steps</h3>
 * <ul>
 *   <li><strong>Normalizer</strong> - Locale/slug to canonical mappings</li>
 *   <li><strong>UnitParser</strong> - Extract grams/ml/servings from attributes and text</li>
 *   <li><strong>ServingCalculator</strong> - Compute servings if missing</li>
 *   <li><strong>PriceCalculator</strong> - Calculate derived price fields</li>
 *   <li><strong>TaxonomyParser</strong> - Extract goal and diet tags</li>
 *   <li><strong>VariationGrouper</strong> - Group product variations</li>
 * </ul>
 * 
 * <p>Each step implements the {@link EnricherStep} interface and can be
 * easily added, removed, or reordered. The pipeline collects warnings and
 * conflicts for monitoring and debugging.
 * 
 * @see EnricherStep
 * @see EnrichmentDelta
 * @see Warn
 * @see RawProduct
 * @see ParsedProduct
 * @see EnrichedProduct
 */
@Service
public class EnrichmentPipeline {
    private static final Logger log = LoggerFactory.getLogger(EnrichmentPipeline.class);

    /** Ordered list of deterministic enrichment steps */
    private final List<EnricherStep> deterministicSteps;
    
    /** Collection of warnings from all enrichment steps */
    private final List<Warn> allWarnings = new ArrayList<>();

    /**
     * Initializes the enrichment pipeline with all deterministic enrichment steps
     * in the correct order for processing.
     */
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

    /**
     * Enriches a raw product through the complete deterministic pipeline.
     * 
     * <p>This method processes the raw product through all deterministic enrichment
     * steps in sequence. Each step can add, modify, or validate fields in the
     * parsed product. Warnings are collected for monitoring and debugging.
     * 
     * <p>The method is idempotent - running it multiple times on the same input
     * will produce the same output.
     * 
     * @param raw The raw product data from WooCommerce
     * @return An enriched product with all deterministic parsing applied
     */
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

    /**
     * Applies an enrichment delta to a parsed product.
     * 
     * <p>This method updates the parsed product with the field changes, confidence
     * scores, and provenance information from an enrichment step.
     * 
     * @param parsed The parsed product to update
     * @param delta The enrichment delta containing updates
     */
    private void applyDelta(ParsedProduct parsed, EnrichmentDelta delta) {
        if (delta.getUpdates() == null) return;

        for (Map.Entry<String, Object> entry : delta.getUpdates().entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            
            // Apply the update using switch statement
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

    /**
     * Gets all warnings collected during enrichment.
     * 
     * @return A copy of all warnings from the enrichment process
     */
    public List<Warn> getAllWarnings() {
        return new ArrayList<>(allWarnings);
    }

    /**
     * Clears all collected warnings.
     * 
     * <p>This method should be called between processing different batches
     * of products to prevent warnings from accumulating.
     */
    public void clearWarnings() {
        allWarnings.clear();
    }
}
