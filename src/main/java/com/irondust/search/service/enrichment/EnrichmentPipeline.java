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

    /** Optional AI enricher */
    private final AIEnricher aiEnricher;

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
            new IngredientTokenizer(),
            new VariationGrouper(),
            new ConflictDetector()
        );
        this.aiEnricher = new AIEnricher();
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

                    java.util.Set<String> updateKeys = java.util.Collections.emptySet();
                    if (delta != null && delta.getUpdates() != null) {
                        updateKeys = delta.getUpdates().keySet();
                    }
                    log.debug("Applied {} to product {}, updates: {}",
                        step.getName(), raw.getId(), updateKeys);
                } catch (Exception e) {
                    log.error("Error applying {} to product {}: {}", 
                        step.getName(), raw.getId(), e.getMessage(), e);
                }
            }
        }

        // Convert to enriched product
        EnrichedProduct enriched = EnrichedProduct.fromParsedProduct(parsed);

        // AI enrichment pass (optional, guarded by env)
        if (aiEnricher.isEnabled()) {
            try {
                Map<String, Object> ai = aiEnricher.enrich(raw, parsed);
                if (!ai.isEmpty()) {
                    // Fill missing core fields only
                    applyAiFill(enriched, ai);
                    // Generate UX fields
                    applyAiGenerate(enriched, ai);
                    // Attach safety/conflicts metadata
                    if (ai.get("safety_flags") instanceof List<?> s) {
                        enriched.setSafety_flags((List<Map<String, Object>>) (List<?>) s);
                    }
                    if (ai.get("conflicts") instanceof List<?> c) {
                        enriched.setConflicts((List<Map<String, Object>>) (List<?>) c);
                        for (Object o : c) {
                            if (o instanceof Map<?, ?> m) {
                                allWarnings.add(Warn.fieldConflict(
                                    raw.getId(),
                                    String.valueOf(m.get("field")),
                                    String.valueOf(m.get("det_value")),
                                    String.valueOf(m.get("ai_value")),
                                    String.valueOf(m.get("evidence"))
                                ));
                            }
                        }
                    }
                    // Metadata
                    if (ai.get("ai_input_hash") instanceof String h) enriched.setAi_input_hash(h);
                    if (ai.get("ai_enrichment_ts") instanceof Number ts) enriched.setAi_enrichment_ts(((Number) ts).longValue());
                    if (ai.get("enrichment_version") instanceof Number v) enriched.setEnrichment_version(((Number) v).intValue());
                }
            } catch (Exception e) {
                log.warn("AI enrichment pass failed for {}: {}", raw.getId(), e.toString());
            }
        }
        
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

    @SuppressWarnings("unchecked")
    private void applyAiFill(EnrichedProduct enriched, Map<String, Object> ai) {
        Object fillObj = ai.get("fill");
        if (!(fillObj instanceof Map<?, ?> fill)) return;
        if (enriched.getForm() == null && fill.get("form") instanceof Map<?, ?> fm) {
            Object v = ((Map<String, Object>) fm).get("value");
            if (v instanceof String s) enriched.setForm(s);
        }
        if (enriched.getFlavor() == null && fill.get("flavor") instanceof Map<?, ?> fl) {
            Object v = ((Map<String, Object>) fl).get("value");
            if (v instanceof String s) enriched.setFlavor(s);
        }
        // Allow high-confidence AI fills for servings and serving_size_g when enabled by env
        boolean allowAiCrit = Boolean.parseBoolean(System.getenv().getOrDefault("AI_ALLOW_HIGH_CONF_CRITICAL", "true"));
        if (allowAiCrit && enriched.getServings() == null && fill.get("servings") instanceof Map<?, ?> sv) {
            Object v = ((Map<String, Object>) sv).get("value");
            Object c = ((Map<String, Object>) sv).get("confidence");
            double conf = (c instanceof Number) ? ((Number) c).doubleValue() : 0.0;
            if (conf >= 0.9 && v instanceof Number n) enriched.setServings(n.intValue());
        }
        if (allowAiCrit && enriched.getServing_size_g() == null && fill.get("serving_size_g") instanceof Map<?, ?> ssg) {
            Object v = ((Map<String, Object>) ssg).get("value");
            Object c = ((Map<String, Object>) ssg).get("confidence");
            double conf = (c instanceof Number) ? ((Number) c).doubleValue() : 0.0;
            if (conf >= 0.9 && v instanceof Number n) enriched.setServing_size_g(n.doubleValue());
        }
        if ((enriched.getIngredients_key() == null || enriched.getIngredients_key().isEmpty()) && fill.get("ingredients_key") instanceof Map<?, ?> ik) {
            Object v = ((Map<String, Object>) ik).get("value");
            if (v instanceof List<?> l) enriched.setIngredients_key((List<String>) (List<?>) l);
        }
        if ((enriched.getGoal_tags() == null || enriched.getGoal_tags().isEmpty()) && fill.get("goal_tags") instanceof Map<?, ?> gt) {
            Object v = ((Map<String, Object>) gt).get("value");
            if (v instanceof List<?> l) enriched.setGoal_tags((List<String>) (List<?>) l);
        }
        if ((enriched.getDiet_tags() == null || enriched.getDiet_tags().isEmpty()) && fill.get("diet_tags") instanceof Map<?, ?> dt) {
            Object v = ((Map<String, Object>) dt).get("value");
            if (v instanceof List<?> l) enriched.setDiet_tags((List<String>) (List<?>) l);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAiGenerate(EnrichedProduct enriched, Map<String, Object> ai) {
        Object genObj = ai.get("generate");
        if (!(genObj instanceof Map<?, ?> gen)) return;
        Object bs = gen.get("benefit_snippet");
        if (bs instanceof String s) enriched.setBenefit_snippet(s);
        Object faq = gen.get("faq");
        if (faq instanceof List<?> l) enriched.setFaq((List<Map<String, String>>) (List<?>) l);
        Object syn = gen.get("synonyms_multi");
        if (syn instanceof Map<?, ?> m) enriched.setSynonyms_multi((Map<String, List<String>>) (Map<?, ?>) m);
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
