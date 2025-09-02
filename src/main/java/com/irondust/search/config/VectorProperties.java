package com.irondust.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vector indexing and retrieval configuration.
 *
 * Properties are prefixed with "vector" in application.yml.
 */
@ConfigurationProperties(prefix = "vector")
public class VectorProperties {
    /** Qdrant base URL, e.g. http://127.0.0.1:6333 */
    private String host;
    /** Qdrant API key (optional) */
    private String apiKey;
    /** Collection name for product vectors */
    private String collectionName = "products_vec";
    /** Embedding model name, e.g. text-embedding-3-small */
    private String embeddingModel = "text-embedding-3-small";
    /** Embedding vector dimension. text-embedding-3-small = 1536 */
    private int embeddingDim = 1536;
    /** Default RRF k parameter for hybrid fusion */
    private int rrfK = 60;
    /** Default vector search limit before fusion */
    private int vectorSearchK = 100;
    /** Max time to wait for vector side (ms) before falling back to Meili-only */
    private int vectorTimeoutMs = 150;
    /** Minimum query length to trigger vector search */
    private int minQueryLength = 3;
    /** Sub-batch size for Qdrant upserts to avoid large payloads */
    private int qdrantUpsertBatchSize = 64;
    /** Max retries for Qdrant upsert failures */
    private int qdrantMaxRetries = 3;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public int getEmbeddingDim() { return embeddingDim; }
    public void setEmbeddingDim(int embeddingDim) { this.embeddingDim = embeddingDim; }

    public int getRrfK() { return rrfK; }
    public void setRrfK(int rrfK) { this.rrfK = rrfK; }

    public int getVectorSearchK() { return vectorSearchK; }
    public void setVectorSearchK(int vectorSearchK) { this.vectorSearchK = vectorSearchK; }

    public int getVectorTimeoutMs() { return vectorTimeoutMs; }
    public void setVectorTimeoutMs(int vectorTimeoutMs) { this.vectorTimeoutMs = vectorTimeoutMs; }

    public int getMinQueryLength() { return minQueryLength; }
    public void setMinQueryLength(int minQueryLength) { this.minQueryLength = minQueryLength; }

    public int getQdrantUpsertBatchSize() { return qdrantUpsertBatchSize; }
    public void setQdrantUpsertBatchSize(int qdrantUpsertBatchSize) { this.qdrantUpsertBatchSize = qdrantUpsertBatchSize; }

    public int getQdrantMaxRetries() { return qdrantMaxRetries; }
    public void setQdrantMaxRetries(int qdrantMaxRetries) { this.qdrantMaxRetries = qdrantMaxRetries; }
}


