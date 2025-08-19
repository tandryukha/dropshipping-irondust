package com.irondust.search.service.enrichment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents partial field updates from an enrichment step.
 * 
 * <p>EnrichmentDelta contains the changes that an enrichment step wants to
 * apply to a product, along with metadata about confidence, sources, and
 * evidence. This allows the enrichment pipeline to track the provenance
 * of each enriched field and apply updates incrementally.
 * 
 * <h3>Components</h3>
 * <ul>
 *   <li><strong>Updates</strong> - Field name to value mappings</li>
 *   <li><strong>Confidence</strong> - Confidence scores (0.0-1.0) for each field</li>
 *   <li><strong>Sources</strong> - Source of each field (attribute, regex, derived, etc.)</li>
 *   <li><strong>Evidence</strong> - Supporting evidence or context for the enrichment</li>
 * </ul>
 * 
 * <p>This class is used by all enrichment steps to communicate their
 * findings back to the enrichment pipeline. The pipeline then applies
 * these updates to the product and tracks provenance information.
 * 
 * @see EnricherStep
 * @see com.irondust.search.service.enrichment.EnrichmentPipeline
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrichmentDelta {
    /** Field name to value mappings for updates */
    private Map<String, Object> updates;
    
    /** Field name to confidence score mappings (0.0-1.0) */
    private Map<String, Double> confidence;
    
    /** Field name to source mappings (attribute, regex, derived, etc.) */
    private Map<String, String> sources;
    
    /** Field name to evidence text mappings */
    private Map<String, String> evidence;

    /**
     * Default constructor for JSON deserialization.
     */
    public EnrichmentDelta() {}

    /**
     * Creates an enrichment delta with all components.
     * 
     * @param updates Field name to value mappings
     * @param confidence Field name to confidence score mappings
     * @param sources Field name to source mappings
     * @param evidence Field name to evidence text mappings
     */
    public EnrichmentDelta(Map<String, Object> updates, Map<String, Double> confidence, 
                          Map<String, String> sources, Map<String, String> evidence) {
        this.updates = updates;
        this.confidence = confidence;
        this.sources = sources;
        this.evidence = evidence;
    }

    // Getters and setters
    public Map<String, Object> getUpdates() { return updates; }
    public void setUpdates(Map<String, Object> updates) { this.updates = updates; }
    public Map<String, Double> getConfidence() { return confidence; }
    public void setConfidence(Map<String, Double> confidence) { this.confidence = confidence; }
    public Map<String, String> getSources() { return sources; }
    public void setSources(Map<String, String> sources) { this.sources = sources; }
    public Map<String, String> getEvidence() { return evidence; }
    public void setEvidence(Map<String, String> evidence) { this.evidence = evidence; }

    /**
     * Creates a simple enrichment delta for a single field update.
     * 
     * <p>This is a convenience method for creating deltas with a single
     * field update. The confidence and source are set, but evidence is null.
     * 
     * @param field The field name to update
     * @param value The new value for the field
     * @param confidence The confidence score (0.0-1.0)
     * @param source The source of this value (attribute, regex, derived, etc.)
     * @return An EnrichmentDelta with the single field update
     */
    public static EnrichmentDelta of(String field, Object value, double confidence, String source) {
        return new EnrichmentDelta(
            Map.of(field, value),
            Map.of(field, confidence),
            Map.of(field, source),
            null
        );
    }

    /**
     * Creates a simple enrichment delta for a single field update with evidence.
     * 
     * <p>This is a convenience method for creating deltas with a single
     * field update including supporting evidence.
     * 
     * @param field The field name to update
     * @param value The new value for the field
     * @param confidence The confidence score (0.0-1.0)
     * @param source The source of this value (attribute, regex, derived, etc.)
     * @param evidence Supporting evidence or context for this enrichment
     * @return An EnrichmentDelta with the single field update and evidence
     */
    public static EnrichmentDelta of(String field, Object value, double confidence, String source, String evidence) {
        return new EnrichmentDelta(
            Map.of(field, value),
            Map.of(field, confidence),
            Map.of(field, source),
            Map.of(field, evidence)
        );
    }
}
