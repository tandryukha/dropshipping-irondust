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
                    java.util.Set<String> fieldsFilledByAi = applyAiFill(enriched, ai);
                    // Apply AI goal_scores when confidence beats thresholds and improves baseline
                    applyAiGoalScores(enriched, ai);
                    // Re-compute derived price metrics if AI filled servings or serving range/size
                    if (fieldsFilledByAi.contains("servings") ||
                        (fieldsFilledByAi.contains("servings_min") && fieldsFilledByAi.contains("servings_max")) ||
                        fieldsFilledByAi.contains("serving_size_g")) {
                        recomputeDerivedAfterAi(enriched);
                    }
                    // Generate UX fields
                    applyAiGenerate(enriched, ai);
                    // Attach safety/conflicts metadata
                    if (ai.get("safety_flags") instanceof List<?> s) {
                        enriched.setSafety_flags((List<Map<String, Object>>) (List<?>) s);
                    }
                    if (ai.get("conflicts") instanceof List<?> c) {
                        // Filter out conflicts where deterministic value is null (not a true conflict)
                        List<Map<String, Object>> conflictsRaw = (List<Map<String, Object>>) (List<?>) c;
                        List<Map<String, Object>> filteredConflicts = new ArrayList<>();
                        for (Object o : conflictsRaw) {
                            if (!(o instanceof Map<?, ?>)) continue;
                            Map<String, Object> m = (Map<String, Object>) o;
                            Object detVal = m.get("det_value");
                            if (detVal == null) {
                                continue; // skip pseudo-conflicts when no deterministic value exists
                            }
                            filteredConflicts.add(m);
                            allWarnings.add(Warn.fieldConflict(
                                raw.getId(),
                                String.valueOf(m.get("field")),
                                String.valueOf(detVal),
                                String.valueOf(m.get("ai_value")),
                                String.valueOf(m.get("evidence"))
                            ));
                        }
                        if (!filteredConflicts.isEmpty()) {
                            enriched.setConflicts(filteredConflicts);
                        }
                    }
                    // If AI filled some critical fields, drop corresponding missing-critical warnings
                    if (fieldsFilledByAi != null && !fieldsFilledByAi.isEmpty()) {
                        allWarnings.removeIf(w -> {
                            if (w == null || !"MISSING_CRITICAL".equals(w.getCode())) return false;
                            if (!raw.getId().equals(w.getProductId())) return false;
                            String field = w.getField();
                            if (fieldsFilledByAi.contains(field)) return true;
                            // Special-case: servings warning satisfied by range fills
                            if ("servings".equals(field) && fieldsFilledByAi.contains("servings_min") && fieldsFilledByAi.contains("servings_max")) {
                                return true;
                            }
                            return false;
                        });
                    }

                    // Post-AI derivations: derive net_weight_g when possible
                    if (enriched.getNet_weight_g() == null || enriched.getNet_weight_g() <= 0) {
                        Double ss = enriched.getServing_size_g();
                        Integer sv = enriched.getServings();
                        if (ss != null && ss > 0 && sv != null && sv > 0) {
                            double derived = ss * sv;
                            if (derived > 0 && derived <= 100000) {
                                enriched.setNet_weight_g(derived);
                            }
                        }
                        if (enriched.getNet_weight_g() == null || enriched.getNet_weight_g() <= 0) {
                            Integer uc = enriched.getUnit_count();
                            Double um = enriched.getUnit_mass_g();
                            if (uc != null && uc > 0 && um != null && um > 0) {
                                double derived = uc * um;
                                if (derived > 0 && derived <= 100000) {
                                    enriched.setNet_weight_g(derived);
                                }
                            }
                        }
                    }
                    // Drop stale missing-critical warnings if net_weight_g is now satisfied
                    if (enriched.getNet_weight_g() != null && enriched.getNet_weight_g() > 0) {
                        allWarnings.removeIf(w -> w != null && "MISSING_CRITICAL".equals(w.getCode())
                            && raw.getId().equals(w.getProductId()) && "net_weight_g".equals(w.getField()));
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
    private java.util.Set<String> applyAiFill(EnrichedProduct enriched, Map<String, Object> ai) {
        java.util.Set<String> fieldsFilled = new java.util.LinkedHashSet<>();
        Object fillObj = ai.get("fill");
        if (!(fillObj instanceof Map<?, ?> fill)) return fieldsFilled;
        // Log fill keys and thresholds for debugging
        log.info("AI fill summary: product={} keys={}", enriched.getId(), fill.keySet());
        if (enriched.getForm() == null && fill.get("form") instanceof Map<?, ?> fm) {
            Object v = ((Map<String, Object>) fm).get("value");
            if (v instanceof String s) { enriched.setForm(s); fieldsFilled.add("form"); }
        }
        if (enriched.getFlavor() == null && fill.get("flavor") instanceof Map<?, ?> fl) {
            Object v = ((Map<String, Object>) fl).get("value");
            if (v instanceof String s) { enriched.setFlavor(s); fieldsFilled.add("flavor"); }
        }
        // Allow AI fills for critical numerics when enabled by env
        boolean allowAiCrit = Boolean.parseBoolean(System.getenv().getOrDefault("AI_ALLOW_HIGH_CONF_CRITICAL", "true"));
        double aiCritThreshold;
        try {
            aiCritThreshold = Double.parseDouble(System.getenv().getOrDefault("AI_CRIT_CONF_THRESHOLD", "0.9"));
        } catch (Exception e) {
            aiCritThreshold = 0.9;
        }
        double servingsThreshold;
        try {
            servingsThreshold = Double.parseDouble(System.getenv().getOrDefault("AI_CONF_THRESHOLD_SERVINGS", "0.75"));
        } catch (Exception e) {
            servingsThreshold = Math.min(0.75, aiCritThreshold);
        }
        double servingSizeThreshold;
        try {
            servingSizeThreshold = Double.parseDouble(System.getenv().getOrDefault("AI_CONF_THRESHOLD_SERVING_SIZE_G", "0.75"));
        } catch (Exception e) {
            servingSizeThreshold = Math.min(0.75, aiCritThreshold);
        }
        log.info("AI thresholds: product={} aiCritThreshold={} servingsThreshold={} servingSizeThreshold={}",
            enriched.getId(), aiCritThreshold, servingsThreshold, servingSizeThreshold);
        if (allowAiCrit && enriched.getServings() == null && fill.containsKey("servings")) {
            Object node = fill.get("servings");
            log.info("AI fill raw: product={} field=servings class={} value={}", enriched.getId(), node != null ? node.getClass().getName() : "null", node);
            boolean applied = false;
            double conf = 0.0;
            Object v = null;
            if (node instanceof Map<?, ?> sv) {
                v = ((Map<String, Object>) sv).get("value");
                Object c = ((Map<String, Object>) sv).get("confidence");
                conf = (c instanceof Number) ? ((Number) c).doubleValue() : 0.0;
                if (conf >= servingsThreshold && v instanceof Number n) { enriched.setServings(n.intValue()); fieldsFilled.add("servings"); applied = true; }
            } else if (node instanceof Number n) {
                v = n;
                conf = Math.max(servingsThreshold, 0.8); // assume reasonable confidence when model returns bare number
                enriched.setServings(n.intValue()); fieldsFilled.add("servings"); applied = true;
            } else if (node instanceof String s) {
                try {
                    int sv = Integer.parseInt(s.trim());
                    v = sv;
                    conf = Math.max(servingsThreshold, 0.8);
                    enriched.setServings(sv); fieldsFilled.add("servings"); applied = true;
                } catch (Exception ignored) {}
            }
            log.info("AI fill attempt: product={} field=servings value={} confidence={} threshold={} applied={}",
                enriched.getId(), v, conf, servingsThreshold, applied);
        }
        if (allowAiCrit && enriched.getServings_min() == null && enriched.getServings() == null && fill.containsKey("servings_min")) {
            Object node = fill.get("servings_min");
            log.info("AI fill raw: product={} field=servings_min class={} value={}", enriched.getId(), node != null ? node.getClass().getName() : "null", node);
            boolean applied = false;
            double conf = 0.0;
            Object v = null;
            if (node instanceof Map<?, ?> smin) {
                v = ((Map<String, Object>) smin).get("value");
                Object c = ((Map<String, Object>) smin).get("confidence");
                conf = (c instanceof Number) ? ((Number) c).doubleValue() : 0.0;
                if (conf >= servingsThreshold && v instanceof Number n) { enriched.setServings_min(n.intValue()); fieldsFilled.add("servings_min"); applied = true; }
            } else if (node instanceof Number n) {
                v = n;
                conf = Math.max(servingsThreshold, 0.8);
                enriched.setServings_min(n.intValue()); fieldsFilled.add("servings_min"); applied = true;
            } else if (node instanceof String s) {
                try { int sv = Integer.parseInt(s.trim()); v = sv; conf = Math.max(servingsThreshold, 0.8); enriched.setServings_min(sv); fieldsFilled.add("servings_min"); applied = true; } catch (Exception ignored) {}
            }
            log.info("AI fill attempt: product={} field=servings_min value={} confidence={} threshold={} applied={}",
                enriched.getId(), v, conf, servingsThreshold, applied);
        }
        if (allowAiCrit && enriched.getServings_max() == null && enriched.getServings() == null && fill.containsKey("servings_max")) {
            Object node = fill.get("servings_max");
            log.info("AI fill raw: product={} field=servings_max class={} value={}", enriched.getId(), node != null ? node.getClass().getName() : "null", node);
            boolean applied = false;
            double conf = 0.0;
            Object v = null;
            if (node instanceof Map<?, ?> smax) {
                v = ((Map<String, Object>) smax).get("value");
                Object c = ((Map<String, Object>) smax).get("confidence");
                conf = (c instanceof Number) ? ((Number) c).doubleValue() : 0.0;
                if (conf >= servingsThreshold && v instanceof Number n) { enriched.setServings_max(n.intValue()); fieldsFilled.add("servings_max"); applied = true; }
            } else if (node instanceof Number n) {
                v = n;
                conf = Math.max(servingsThreshold, 0.8);
                enriched.setServings_max(n.intValue()); fieldsFilled.add("servings_max"); applied = true;
            } else if (node instanceof String s) {
                try { int sv = Integer.parseInt(s.trim()); v = sv; conf = Math.max(servingsThreshold, 0.8); enriched.setServings_max(sv); fieldsFilled.add("servings_max"); applied = true; } catch (Exception ignored) {}
            }
            log.info("AI fill attempt: product={} field=servings_max value={} confidence={} threshold={} applied={}",
                enriched.getId(), v, conf, servingsThreshold, applied);
        }
        if (allowAiCrit && enriched.getServing_size_g() == null && fill.containsKey("serving_size_g")) {
            Object node = fill.get("serving_size_g");
            log.info("AI fill raw: product={} field=serving_size_g class={} value={}", enriched.getId(), node != null ? node.getClass().getName() : "null", node);
            boolean applied = false;
            double conf = 0.0;
            Object v = null;
            if (node instanceof Map<?, ?> ssg) {
                v = ((Map<String, Object>) ssg).get("value");
                Object c = ((Map<String, Object>) ssg).get("confidence");
                conf = (c instanceof Number) ? ((Number) c).doubleValue() : 0.0;
                if (conf >= servingSizeThreshold && v instanceof Number n) { enriched.setServing_size_g(n.doubleValue()); fieldsFilled.add("serving_size_g"); applied = true; }
            } else if (node instanceof Number n) {
                v = n;
                conf = Math.max(servingSizeThreshold, 0.8);
                enriched.setServing_size_g(n.doubleValue()); fieldsFilled.add("serving_size_g"); applied = true;
            } else if (node instanceof String s) {
                try { double sv = Double.parseDouble(s.trim().replace(",", ".")); v = sv; conf = Math.max(servingSizeThreshold, 0.8); enriched.setServing_size_g(sv); fieldsFilled.add("serving_size_g"); applied = true; } catch (Exception ignored) {}
            }
            log.info("AI fill attempt: product={} field=serving_size_g value={} confidence={} threshold={} applied={}",
                enriched.getId(), v, conf, servingSizeThreshold, applied);
        }
        if ((enriched.getIngredients_key() == null || enriched.getIngredients_key().isEmpty()) && fill.get("ingredients_key") instanceof Map<?, ?> ik) {
            Object v = ((Map<String, Object>) ik).get("value");
            if (v instanceof List<?> l) { enriched.setIngredients_key((List<String>) (List<?>) l); fieldsFilled.add("ingredients_key"); }
        }
        if ((enriched.getGoal_tags() == null || enriched.getGoal_tags().isEmpty()) && fill.get("goal_tags") instanceof Map<?, ?> gt) {
            Object v = ((Map<String, Object>) gt).get("value");
            if (v instanceof List<?> l) { enriched.setGoal_tags((List<String>) (List<?>) l); fieldsFilled.add("goal_tags"); }
        }
        if ((enriched.getDiet_tags() == null || enriched.getDiet_tags().isEmpty()) && fill.get("diet_tags") instanceof Map<?, ?> dt) {
            Object v = ((Map<String, Object>) dt).get("value");
            if (v instanceof List<?> l) { enriched.setDiet_tags((List<String>) (List<?>) l); fieldsFilled.add("diet_tags"); }
        }
        return fieldsFilled;
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

    @SuppressWarnings("unchecked")
    private void applyAiGoalScores(EnrichedProduct enriched, Map<String, Object> ai) {
        Object gs = ai.get("goal_scores");
        if (!(gs instanceof Map<?, ?> gmap)) return;
        double threshold;
        try {
            threshold = Double.parseDouble(System.getenv().getOrDefault("AI_GOAL_CONF_THRESHOLD", "0.7"));
        } catch (Exception e) { threshold = 0.7; }

        java.util.Map<String, java.util.function.BiConsumer<EnrichedProduct, Double>> setters = new java.util.HashMap<>();
        setters.put("preworkout", (p, v) -> p.setGoal_preworkout_score(v));
        setters.put("strength", (p, v) -> p.setGoal_strength_score(v));
        setters.put("endurance", (p, v) -> p.setGoal_endurance_score(v));
        setters.put("lean_muscle", (p, v) -> p.setGoal_lean_muscle_score(v));
        setters.put("recovery", (p, v) -> p.setGoal_recovery_score(v));
        setters.put("weight_loss", (p, v) -> p.setGoal_weight_loss_score(v));
        setters.put("wellness", (p, v) -> p.setGoal_wellness_score(v));

        java.util.Map<String, java.util.function.Function<EnrichedProduct, Double>> getters = new java.util.HashMap<>();
        getters.put("preworkout", EnrichedProduct::getGoal_preworkout_score);
        getters.put("strength", EnrichedProduct::getGoal_strength_score);
        getters.put("endurance", EnrichedProduct::getGoal_endurance_score);
        getters.put("lean_muscle", EnrichedProduct::getGoal_lean_muscle_score);
        getters.put("recovery", EnrichedProduct::getGoal_recovery_score);
        getters.put("weight_loss", EnrichedProduct::getGoal_weight_loss_score);
        getters.put("wellness", EnrichedProduct::getGoal_wellness_score);

        for (var entry : getters.entrySet()) {
            String goal = entry.getKey();
            Object node = ((Map<String, Object>) (Map<?, ?>) gmap).get(goal);
            if (!(node instanceof Map<?, ?> m)) continue;
            Object sc = m.get("score");
            Object cf = m.get("confidence");
            double score = (sc instanceof Number) ? ((Number) sc).doubleValue() : -1.0;
            double conf = (cf instanceof Number) ? ((Number) cf).doubleValue() : 0.0;
            if (score < 0.0 || score > 1.0 || conf < threshold) continue;
            Double current = entry.getValue().apply(enriched);
            double cur = current != null ? current : 0.0;
            if (score > cur) {
                setters.get(goal).accept(enriched, score);
            }
        }
    }

    /**
     * Recomputes derived fields on the enriched product after AI fills critical numerics.
     */
    private void recomputeDerivedAfterAi(EnrichedProduct enriched) {
        // Derive servings from serving_size_g if possible (post-AI)
        if (enriched.getServings() == null && (enriched.getServings_min() == null || enriched.getServings_max() == null)
                && enriched.getNet_weight_g() != null && enriched.getServing_size_g() != null
                && enriched.getNet_weight_g() > 0 && enriched.getServing_size_g() > 0) {
            double s = enriched.getNet_weight_g() / enriched.getServing_size_g();
            if (s > 0 && s <= 1000) {
                enriched.setServings((int) Math.round(s));
            }
        }

        // Ensure price (euros)
        if (enriched.getPrice() == null && enriched.getPrice_cents() != null) {
            enriched.setPrice(enriched.getPrice_cents() / 100.0);
        }
        Double price = enriched.getPrice();
        if (price != null) {
            // Exact servings
            if (enriched.getServings() != null && enriched.getServings() > 0) {
                double pps = Math.round((price / enriched.getServings()) * 100.0) / 100.0;
                enriched.setPrice_per_serving(pps);
            }
            // Range
            if (enriched.getServings_min() != null && enriched.getServings_max() != null
                    && enriched.getServings_min() > 0 && enriched.getServings_max() > 0) {
                double min = Math.round((price / enriched.getServings_max()) * 100.0) / 100.0;
                double max = Math.round((price / enriched.getServings_min()) * 100.0) / 100.0;
                enriched.setPrice_per_serving_min(min);
                enriched.setPrice_per_serving_max(max);
            }
            // price_per_100g if weight present
            if (enriched.getNet_weight_g() != null && enriched.getNet_weight_g() > 0) {
                double p100 = Math.round(((price * 100) / enriched.getNet_weight_g()) * 100.0) / 100.0;
                enriched.setPrice_per_100g(p100);
            }
        }
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
                case "servings_min":
                    parsed.setServings_min((Integer) value);
                    break;
                case "servings_max":
                    parsed.setServings_max((Integer) value);
                    break;
                case "serving_size_g":
                    parsed.setServing_size_g((Double) value);
                    break;
                case "unit_count":
                    parsed.setUnit_count((Integer) value);
                    break;
                case "units_per_serving":
                    parsed.setUnits_per_serving((Integer) value);
                    break;
                case "unit_mass_g":
                    parsed.setUnit_mass_g((Double) value);
                    break;
                case "price_per_unit":
                    parsed.setPrice_per_unit((Double) value);
                    break;
                case "regular_price_cents":
                    parsed.setRegular_price_cents((Integer) value);
                    break;
                case "sale_price_cents":
                    parsed.setSale_price_cents((Integer) value);
                    break;
                case "price":
                    parsed.setPrice((Double) value);
                    break;
                case "price_per_serving":
                    parsed.setPrice_per_serving((Double) value);
                    break;
                case "price_per_serving_min":
                    parsed.setPrice_per_serving_min((Double) value);
                    break;
                case "price_per_serving_max":
                    parsed.setPrice_per_serving_max((Double) value);
                    break;
                case "price_per_100g":
                    parsed.setPrice_per_100g((Double) value);
                    break;
                case "discount_pct":
                    parsed.setDiscount_pct((Double) value);
                    break;
                case "is_on_sale":
                    parsed.setIs_on_sale((Boolean) value);
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
                case "goal_preworkout_score":
                    parsed.setGoal_preworkout_score((Double) value);
                    break;
                case "goal_strength_score":
                    parsed.setGoal_strength_score((Double) value);
                    break;
                case "goal_endurance_score":
                    parsed.setGoal_endurance_score((Double) value);
                    break;
                case "goal_lean_muscle_score":
                    parsed.setGoal_lean_muscle_score((Double) value);
                    break;
                case "goal_recovery_score":
                    parsed.setGoal_recovery_score((Double) value);
                    break;
                case "goal_weight_loss_score":
                    parsed.setGoal_weight_loss_score((Double) value);
                    break;
                case "goal_wellness_score":
                    parsed.setGoal_wellness_score((Double) value);
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
