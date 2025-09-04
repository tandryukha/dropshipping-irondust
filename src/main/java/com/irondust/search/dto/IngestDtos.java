package com.irondust.search.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class IngestDtos {
    public static class TargetedIngestRequest {
        @NotNull
        @NotEmpty
        private List<Long> ids; // Woo product numeric IDs

        public List<Long> getIds() { return ids; }
        public void setIds(List<Long> ids) { this.ids = ids; }
    }

    /** Per-product ingestion report: warnings and AI/deterministic conflicts */
    public static class ProductReport {
        private String id; // wc_<productId>
        private List<String> warnings; // human-readable warnings accumulated by pipeline
        private List<java.util.Map<String, Object>> conflicts; // AI conflict objects if present

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        public List<java.util.Map<String, Object>> getConflicts() { return conflicts; }
        public void setConflicts(List<java.util.Map<String, Object>> conflicts) { this.conflicts = conflicts; }
    }

    /** Overall ingestion report returned by the ingest endpoints */
    public static class IngestReport {
        private int indexed; // number of documents indexed
        private int warnings_total; // total warnings across all products
        private int conflicts_total; // total conflicts across all products
        private int ignored_count; // number of products ignored (e.g., gift cards)
        private java.util.List<String> ignored_ids; // list of ignored product IDs (wc_*)
        private List<ProductReport> products; // per-product details

        /**
         * Per-model AI token usage and approximate cost.
         * Structure: model -> { prompt_tokens, completion_tokens, total_tokens, cost_usd }
         */
        private java.util.Map<String, java.util.Map<String, Object>> ai_usage_per_model;

        /** Total approximate AI cost in USD across all models for this ingest run. */
        private double ai_cost_total_usd;

        public int getIndexed() { return indexed; }
        public void setIndexed(int indexed) { this.indexed = indexed; }
        public int getWarnings_total() { return warnings_total; }
        public void setWarnings_total(int warnings_total) { this.warnings_total = warnings_total; }
        public int getConflicts_total() { return conflicts_total; }
        public void setConflicts_total(int conflicts_total) { this.conflicts_total = conflicts_total; }
        public int getIgnored_count() { return ignored_count; }
        public void setIgnored_count(int ignored_count) { this.ignored_count = ignored_count; }
        public java.util.List<String> getIgnored_ids() { return ignored_ids; }
        public void setIgnored_ids(java.util.List<String> ignored_ids) { this.ignored_ids = ignored_ids; }
        public List<ProductReport> getProducts() { return products; }
        public void setProducts(List<ProductReport> products) { this.products = products; }

        public java.util.Map<String, java.util.Map<String, Object>> getAi_usage_per_model() { return ai_usage_per_model; }
        public void setAi_usage_per_model(java.util.Map<String, java.util.Map<String, Object>> ai_usage_per_model) { this.ai_usage_per_model = ai_usage_per_model; }
        public double getAi_cost_total_usd() { return ai_cost_total_usd; }
        public void setAi_cost_total_usd(double ai_cost_total_usd) { this.ai_cost_total_usd = ai_cost_total_usd; }
    }
}




