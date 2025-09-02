package com.irondust.search.service;

import com.irondust.search.config.VectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Qdrant client for collection management, upserts, and vector search.
 */
@Service
public class QdrantService {
    private static final Logger log = LoggerFactory.getLogger(QdrantService.class);
    private final WebClient qdrantClient;
    private final VectorProperties vectorProperties;

    public QdrantService(@Qualifier("qdrantClient") WebClient qdrantClient, VectorProperties vectorProperties) {
        this.qdrantClient = qdrantClient;
        this.vectorProperties = vectorProperties;
    }

    public Mono<Boolean> isHealthy() {
        return qdrantClient.get().uri("/healthz")
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> Boolean.TRUE)
                .onErrorReturn(Boolean.FALSE);
    }

    public Mono<Void> ensureCollection() {
        String name = vectorProperties.getCollectionName();
        Map<String, Object> create = Map.of(
                "vectors", Map.of(
                        "size", vectorProperties.getEmbeddingDim(),
                        "distance", "Cosine"
                )
        );
        return qdrantClient.put().uri("/collections/{name}", name)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    public Mono<Void> upsertBatch(List<QdrantPoint> points) {
        if (points == null || points.isEmpty()) return Mono.empty();
        String name = vectorProperties.getCollectionName();
        int chunk = Math.max(1, vectorProperties.getQdrantUpsertBatchSize());
        List<List<QdrantPoint>> batches = new ArrayList<>();
        for (int i = 0; i < points.size(); i += chunk) {
            batches.add(points.subList(i, Math.min(points.size(), i + chunk)));
        }
        return Flux.fromIterable(batches)
                .concatMap(batch -> {
                    List<Map<String, Object>> pts = new ArrayList<>();
                    for (QdrantPoint p : batch) {
                        Map<String, Object> pt = new LinkedHashMap<>();
                        pt.put("id", p.id);
                        pt.put("vector", p.vector);
                        pt.put("payload", p.payload);
                        pts.add(pt);
                    }
                    Map<String, Object> payload = Map.of("points", pts);
                    return qdrantClient.put().uri("/collections/{name}/points?wait=true", name)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BodyInserters.fromValue(payload))
                            .retrieve()
                            .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), resp ->
                                    resp.bodyToMono(String.class).defaultIfEmpty("")
                                            .flatMap(body -> {
                                                log.error("Qdrant upsert error: status={} body_length={} batch_size={}", resp.statusCode(), body != null ? body.length() : 0, pts.size());
                                                return Mono.error(new RuntimeException("Qdrant upsert failed"));
                                            })
                            )
                            .bodyToMono(Map.class)
                            .doOnNext(r -> log.info("Qdrant upsert ok: points={} collection={}", pts.size(), vectorProperties.getCollectionName()))
                            .retryWhen(reactor.util.retry.Retry.max(vectorProperties.getQdrantMaxRetries())
                                    .filter(ex -> true)
                                    .doBeforeRetry(sig -> log.warn("Retrying Qdrant upsert batch attempt={} size={}", sig.totalRetriesInARow() + 1, pts.size())))
                            .then();
                })
                .then();
    }

    public Mono<List<SearchResult>> search(float[] queryVector, Map<String, Object> filter, int limit) {
        String name = vectorProperties.getCollectionName();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vector", queryVector);
        payload.put("limit", limit);
        // Only return minimal payload to map back to doc id
        payload.put("with_payload", Map.of("include", java.util.List.of("doc_id","id")));
        payload.put("with_vectors", false);
        if (filter != null && !filter.isEmpty()) payload.put("filter", filter);
        return qdrantClient.post().uri("/collections/{name}/points/search", name)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(resp -> {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) resp.getOrDefault("result", List.of());
                    List<SearchResult> out = new ArrayList<>();
                    for (Map<String, Object> r : results) {
                        String id = String.valueOf(r.get("id"));
                        double score = r.get("score") instanceof Number n ? n.doubleValue() : 0.0;
                        Map<String, Object> payloadMap = (Map<String, Object>) r.get("payload");
                        out.add(new SearchResult(id, score, payloadMap));
                    }
                    return out;
                });
    }

    public static String toPointIdForDocId(String docId) {
        try {
            java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(docId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return uuid.toString();
        } catch (Exception e) {
            return docId; // fallback
        }
    }

    public Mono<List<SearchResult>> recommendByPointId(String pointId, Map<String, Object> filter, int limit) {
        String name = vectorProperties.getCollectionName();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("positive", java.util.List.of(pointId));
        payload.put("limit", limit);
        // Ensure payloads are returned so we can map back to document ids
        payload.put("with_payload", true);
        payload.put("with_vectors", false);
        if (filter != null && !filter.isEmpty()) payload.put("filter", filter);
        return qdrantClient.post().uri("/collections/{name}/points/recommend", name)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(resp -> {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) resp.getOrDefault("result", List.of());
                    List<SearchResult> out = new ArrayList<>();
                    for (Map<String, Object> r : results) {
                        String id = String.valueOf(r.get("id"));
                        double score = r.get("score") instanceof Number n ? n.doubleValue() : 0.0;
                        Map<String, Object> payloadMap = (Map<String, Object>) r.get("payload");
                        out.add(new SearchResult(id, score, payloadMap));
                    }
                    return out;
                });
    }

    public Mono<List<SearchResult>> recommendByDocId(String docId, Map<String, Object> filter, int limit) {
        String pointId = toPointIdForDocId(docId);
        return recommendByPointId(pointId, filter, limit);
    }

    public static class QdrantPoint {
        public String id;
        public float[] vector;
        public Map<String, Object> payload;
        public QdrantPoint(String id, float[] vector, Map<String, Object> payload) {
            this.id = id; this.vector = vector; this.payload = payload;
        }
        public String getId() { return id; }
        public float[] getVector() { return vector; }
        public Map<String, Object> getPayload() { return payload; }
    }

    public static class SearchResult {
        public final String id;
        public final double score;
        public final Map<String, Object> payload;
        public SearchResult(String id, double score, Map<String, Object> payload) {
            this.id = id; this.score = score; this.payload = payload;
        }
    }
}


