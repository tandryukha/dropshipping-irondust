package com.irondust.search.service.enrichment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrichmentDelta {
    private Map<String, Object> updates;
    private Map<String, Double> confidence;
    private Map<String, String> sources;
    private Map<String, String> evidence;

    public EnrichmentDelta() {}

    public EnrichmentDelta(Map<String, Object> updates, Map<String, Double> confidence, 
                          Map<String, String> sources, Map<String, String> evidence) {
        this.updates = updates;
        this.confidence = confidence;
        this.sources = sources;
        this.evidence = evidence;
    }

    public Map<String, Object> getUpdates() { return updates; }
    public void setUpdates(Map<String, Object> updates) { this.updates = updates; }
    public Map<String, Double> getConfidence() { return confidence; }
    public void setConfidence(Map<String, Double> confidence) { this.confidence = confidence; }
    public Map<String, String> getSources() { return sources; }
    public void setSources(Map<String, String> sources) { this.sources = sources; }
    public Map<String, String> getEvidence() { return evidence; }
    public void setEvidence(Map<String, String> evidence) { this.evidence = evidence; }

    // Helper method to create a simple delta
    public static EnrichmentDelta of(String field, Object value, double confidence, String source) {
        return new EnrichmentDelta(
            Map.of(field, value),
            Map.of(field, confidence),
            Map.of(field, source),
            null
        );
    }

    public static EnrichmentDelta of(String field, Object value, double confidence, String source, String evidence) {
        return new EnrichmentDelta(
            Map.of(field, value),
            Map.of(field, confidence),
            Map.of(field, source),
            Map.of(field, evidence)
        );
    }
}
