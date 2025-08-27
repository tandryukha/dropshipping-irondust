package com.irondust.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String adminKey;
    private String baseUrl;
    private int perPage;
    private String indexName;
    // Ingest tuning
    private int ingestParallelism;
    private int meiliConcurrentUpdates;
    private int uploadChunkSize;

    public String getAdminKey() {
        return adminKey;
    }

    public void setAdminKey(String adminKey) {
        this.adminKey = adminKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getPerPage() {
        return perPage;
    }

    public void setPerPage(int perPage) {
        this.perPage = perPage;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public int getIngestParallelism() {
        return ingestParallelism;
    }

    public void setIngestParallelism(int ingestParallelism) {
        this.ingestParallelism = ingestParallelism;
    }

    public int getMeiliConcurrentUpdates() {
        return meiliConcurrentUpdates;
    }

    public void setMeiliConcurrentUpdates(int meiliConcurrentUpdates) {
        this.meiliConcurrentUpdates = meiliConcurrentUpdates;
    }

    public int getUploadChunkSize() {
        return uploadChunkSize;
    }

    public void setUploadChunkSize(int uploadChunkSize) {
        this.uploadChunkSize = uploadChunkSize;
    }
}



