package com.irondust.search.service;

import com.irondust.search.config.VectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Builds vector embeddings from existing Meilisearch documents and stores them in Qdrant.
 * Supports partial re-index by ids and idempotent upserts.
 */
@Service
public class VectorIndexService {
    private static final Logger log = LoggerFactory.getLogger(VectorIndexService.class);

    private final MeiliDocumentService meiliDocumentService;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final VectorProperties vectorProperties;

    public VectorIndexService(MeiliDocumentService meiliDocumentService, EmbeddingService embeddingService,
                              QdrantService qdrantService, VectorProperties vectorProperties) {
        this.meiliDocumentService = meiliDocumentService;
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.vectorProperties = vectorProperties;
    }

    public Mono<Void> ensureReady() {
        return qdrantService.ensureCollection();
    }

    public Mono<Void> reindexAll(int batchSize) {
        int bs = Math.max(1, batchSize);
        return ensureReady()
                .thenMany(
                        meiliDocumentService.countDocuments()
                                .doOnNext(total -> log.info("Vector reindex-all starting: total_meili_docs={} batchSize={} model={} dim={} collection={}",
                                        total, bs, vectorProperties.getEmbeddingModel(), vectorProperties.getEmbeddingDim(), vectorProperties.getCollectionName()))
                                .thenMany(meiliDocumentService.streamAllBasic())
                )
                .index()
                .buffer(bs)
                .concatMap(buffered -> {
                    long lastIdx = buffered.get(buffered.size() - 1).getT1();
                    List<Map<String, Object>> docs = new ArrayList<>();
                    for (var t : buffered) docs.add(t.getT2());
                    return upsertVectorBatch(docs)
                            .doOnSuccess(v -> log.info("Vector reindex progress: processed={} (+{})", lastIdx + 1, buffered.size()));
                })
                .then(Mono.fromRunnable(() -> log.info("Vector reindex-all completed for collection={}", vectorProperties.getCollectionName())));
    }

    public Mono<Void> reindexByIds(List<String> ids, int batchSize) {
        if (ids == null || ids.isEmpty()) return Mono.empty();
        int bs = Math.max(1, batchSize);
        return ensureReady()
                .thenMany(Flux.fromIterable(ids))
                .concatMap(meiliDocumentService::getDocumentRaw)
                .index()
                .buffer(bs)
                .concatMap(buffered -> {
                    long lastIdx = buffered.get(buffered.size() - 1).getT1();
                    List<Map<String, Object>> docs = new ArrayList<>();
                    for (var t : buffered) docs.add(t.getT2());
                    return upsertVectorBatch(docs)
                            .doOnSuccess(v -> log.info("Vector reindex by ids progress: processed={} (+{})", lastIdx + 1, buffered.size()));
                })
                .then(Mono.fromRunnable(() -> log.info("Vector reindex by ids completed for collection={}", vectorProperties.getCollectionName())));
    }

    private Mono<Void> upsertVectorBatch(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return Mono.empty();
        List<QdrantService.QdrantPoint> points = new ArrayList<>();
        for (Map<String, Object> d : docs) {
            String docId = String.valueOf(d.get("id"));
            String pointId = java.util.UUID.nameUUIDFromBytes(docId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            String text = buildEmbeddingText(d);
            float[] vec = embeddingService.embedText(text);
            Map<String, Object> payload = buildPayload(d);
            // Preserve original document id for downstream joins
            payload.put("doc_id", docId);
            points.add(new QdrantService.QdrantPoint(pointId, vec, payload));
        }
        return qdrantService.upsertBatch(points);
    }

    static String buildEmbeddingText(Map<String, Object> doc) {
        List<String> parts = new ArrayList<>();
        putString(parts, doc.get("name"));
        putString(parts, doc.get("brand_name"));
        putList(parts, (List<String>) doc.get("categories_names"));
        putList(parts, (List<String>) doc.get("ingredients_key"));
        putList(parts, (List<String>) doc.get("goal_tags"));
        putList(parts, (List<String>) doc.get("diet_tags"));
        putString(parts, doc.get("benefit_snippet"));
        return String.join(" | ", parts);
    }

    private Map<String, Object> buildPayload(Map<String, Object> doc) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", doc.get("id"));
        p.put("brand", doc.get("brand_name"));
        p.put("categories_slugs", doc.get("categories_slugs"));
        p.put("form", doc.get("form"));
        p.put("diet_parity", firstOf((List<String>) doc.get("diet_tags")));
        p.put("parent_id", doc.get("parent_id"));
        p.put("in_stock", doc.get("in_stock"));
        p.put("price_cents", doc.get("price_cents"));
        return p;
    }

    private static String firstOf(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    private static void putString(List<String> parts, Object v) {
        if (v == null) return;
        String s = String.valueOf(v).trim();
        if (!s.isEmpty()) parts.add(s);
    }

    private static void putList(List<String> parts, List<String> list) {
        if (list == null) return;
        for (String s : list) if (s != null && !s.isBlank()) parts.add(s.trim());
    }
}


