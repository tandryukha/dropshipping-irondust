package com.irondust.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String adminKey;
    /**
     * Username for Basic Auth protecting Admin UI and Admin API endpoints.
     */
    private String adminUsername;
    /**
     * Password for Basic Auth protecting Admin UI and Admin API endpoints.
     */
    private String adminPassword;
    private String baseUrl;
    private int perPage;
    private String indexName;
    /**
     * Separate Meilisearch index for editorial and external content (Wikipedia, FDA, etc.).
     * Defaults to "content" when not set.
     */
    private String contentIndexName;
    // Ingest tuning
    private int ingestParallelism;
    private int meiliConcurrentUpdates;
    private int uploadChunkSize;
    /**
     * Directory where full-ingest reports are saved as timestamped JSON files.
     * Defaults to "tmp/ingest-history" when not set.
     */
    private String ingestHistoryDir;
    /**
     * Filesystem path where Admin Feature Flags are persisted as JSON.
     * Defaults to "tmp/feature-flags.json" when not set.
     */
    private String featureFlagsPath;

    public String getAdminKey() {
        return adminKey;
    }

    public void setAdminKey(String adminKey) {
        this.adminKey = adminKey;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getFeatureFlagsPath() {
        return featureFlagsPath;
    }

    public void setFeatureFlagsPath(String featureFlagsPath) {
        this.featureFlagsPath = featureFlagsPath;
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

    public String getContentIndexName() {
        return contentIndexName;
    }

    public void setContentIndexName(String contentIndexName) {
        this.contentIndexName = contentIndexName;
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

    public String getIngestHistoryDir() {
        return ingestHistoryDir;
    }

    public void setIngestHistoryDir(String ingestHistoryDir) {
        this.ingestHistoryDir = ingestHistoryDir;
    }
}



