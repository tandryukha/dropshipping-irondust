package com.irondust.search.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrichedProduct extends ParsedProduct {
    // AI-generated fields
    private String benefit_snippet;
    private List<Map<String, String>> faq; // [{"q": "...", "a": "..."}]
    private Map<String, List<String>> synonyms_multi; // {"en": [...], "ru": [...], "et": [...]}
    private List<Map<String, Object>> safety_flags; // [{"flag": "...", "confidence": 0.9, "evidence": "..."}]
    private List<Map<String, Object>> conflicts; // [{"field": "...", "det_value": "...", "ai_value": "...", "evidence": "..."}]
    private String ai_notes;
    
    // Metadata
    private String ai_input_hash;
    private Long ai_enrichment_ts;
    private Integer enrichment_version;

    // Getters and setters for AI-generated fields
    public String getBenefit_snippet() { return benefit_snippet; }
    public void setBenefit_snippet(String benefit_snippet) { this.benefit_snippet = benefit_snippet; }
    public List<Map<String, String>> getFaq() { return faq; }
    public void setFaq(List<Map<String, String>> faq) { this.faq = faq; }
    public Map<String, List<String>> getSynonyms_multi() { return synonyms_multi; }
    public void setSynonyms_multi(Map<String, List<String>> synonyms_multi) { this.synonyms_multi = synonyms_multi; }
    public List<Map<String, Object>> getSafety_flags() { return safety_flags; }
    public void setSafety_flags(List<Map<String, Object>> safety_flags) { this.safety_flags = safety_flags; }
    public List<Map<String, Object>> getConflicts() { return conflicts; }
    public void setConflicts(List<Map<String, Object>> conflicts) { this.conflicts = conflicts; }
    public String getAi_notes() { return ai_notes; }
    public void setAi_notes(String ai_notes) { this.ai_notes = ai_notes; }
    public String getAi_input_hash() { return ai_input_hash; }
    public void setAi_input_hash(String ai_input_hash) { this.ai_input_hash = ai_input_hash; }
    public Long getAi_enrichment_ts() { return ai_enrichment_ts; }
    public void setAi_enrichment_ts(Long ai_enrichment_ts) { this.ai_enrichment_ts = ai_enrichment_ts; }
    public Integer getEnrichment_version() { return enrichment_version; }
    public void setEnrichment_version(Integer enrichment_version) { this.enrichment_version = enrichment_version; }

    // Helper method to convert from ParsedProduct
    public static EnrichedProduct fromParsedProduct(ParsedProduct parsed) {
        EnrichedProduct enriched = new EnrichedProduct();
        // Copy all fields from ParsedProduct
        enriched.setId(parsed.getId());
        enriched.setType(parsed.getType());
        enriched.setSku(parsed.getSku());
        enriched.setSlug(parsed.getSlug());
        enriched.setName(parsed.getName());
        enriched.setPermalink(parsed.getPermalink());
        enriched.setDescription(parsed.getDescription());
        enriched.setPrice_cents(parsed.getPrice_cents());
        enriched.setCurrency(parsed.getCurrency());
        enriched.setIn_stock(parsed.getIn_stock());
        enriched.setLow_stock_remaining(parsed.getLow_stock_remaining());
        enriched.setRating(parsed.getRating());
        enriched.setReview_count(parsed.getReview_count());
        enriched.setImages(parsed.getImages());
        enriched.setCategories_ids(parsed.getCategories_ids());
        enriched.setCategories_slugs(parsed.getCategories_slugs());
        enriched.setCategories_names(parsed.getCategories_names());
        enriched.setBrand_slug(parsed.getBrand_slug());
        enriched.setBrand_name(parsed.getBrand_name());
        enriched.setDynamic_attrs(parsed.getDynamic_attrs());
        enriched.setSearch_text(parsed.getSearch_text());
        enriched.setForm(parsed.getForm());
        enriched.setFlavor(parsed.getFlavor());
        enriched.setNet_weight_g(parsed.getNet_weight_g());
        enriched.setServings(parsed.getServings());
        enriched.setServing_size_g(parsed.getServing_size_g());
        enriched.setPrice(parsed.getPrice());
        enriched.setPrice_per_serving(parsed.getPrice_per_serving());
        enriched.setPrice_per_100g(parsed.getPrice_per_100g());
        enriched.setGoal_tags(parsed.getGoal_tags());
        enriched.setDiet_tags(parsed.getDiet_tags());
        enriched.setIngredients_key(parsed.getIngredients_key());
        enriched.setParent_id(parsed.getParent_id());
        enriched.setVariant_group_id(parsed.getVariant_group_id());
        enriched.setWarnings(parsed.getWarnings());
        enriched.setProvenance(parsed.getProvenance());
        return enriched;
    }
}
