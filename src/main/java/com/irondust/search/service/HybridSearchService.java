package com.irondust.search.service;

import com.irondust.search.config.VectorProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * Hybrid search: run Meili (BM25) and Qdrant kNN in parallel, then fuse via Reciprocal Rank Fusion.
 */
@Service
public class HybridSearchService {
    private final MeiliService meiliService;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final VectorProperties vectorProperties;

    public HybridSearchService(MeiliService meiliService, EmbeddingService embeddingService, QdrantService qdrantService, VectorProperties vectorProperties) {
        this.meiliService = meiliService;
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.vectorProperties = vectorProperties;
    }

    public Mono<Map<String, Object>> search(String q, String filter, List<String> sort, int page, int size, List<String> facets) {
        // Run Meili search
        Mono<Map<String, Object>> meiliMono = meiliService.searchRaw(q, filter, sort, page, size, facets);

        // Run vector search if query present and long enough; do not block request if vector side is slow
        boolean vectorEligible = q != null && !q.isBlank() && q.trim().length() >= Math.max(1, vectorProperties.getMinQueryLength()) && embeddingService.isEnabled();
        Mono<List<QdrantService.SearchResult>> vectorMono = vectorEligible
                ? Mono.fromSupplier(() -> embeddingService.embedText(q))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(vec -> {
                        int dynamicK = Math.min(Math.max(20, vectorProperties.getVectorSearchK()), 100);
                        return qdrantService.search(vec, buildVectorFilter(filter), dynamicK);
                    })
                    .timeout(java.time.Duration.ofMillis(Math.max(50, vectorProperties.getVectorTimeoutMs())))
                    .onErrorResume(e -> Mono.just(List.of()))
                : Mono.just(List.of());

        return Mono.zip(meiliMono, vectorMono)
                .map(tuple -> fuseRRF(tuple.getT1(), tuple.getT2(), size));
    }

    private Map<String, Object> fuseRRF(Map<String, Object> meili, List<QdrantService.SearchResult> vecResults, int size) {
        Map<String, Double> rrf = new LinkedHashMap<>();
        int k = Math.max(1, vectorProperties.getRrfK());

        // Add Meili ranks
        List<Map<String, Object>> hits = (List<Map<String, Object>>) meili.getOrDefault("hits", List.of());
        for (int i = 0; i < hits.size(); i++) {
            Map<String, Object> h = hits.get(i);
            String id = String.valueOf(h.get("id"));
            double score = 1.0 / (k + i + 1);
            rrf.merge(id, score, Double::sum);
        }

        // Add Vector ranks (map Qdrant point -> original document id via payload)
        for (int i = 0; i < vecResults.size(); i++) {
            QdrantService.SearchResult r = vecResults.get(i);
            String targetId = r.id;
            if (r.payload != null) {
                Object did = r.payload.get("doc_id");
                if (did == null) did = r.payload.get("id");
                if (did != null) targetId = String.valueOf(did);
            }
            double score = 1.0 / (k + i + 1);
            rrf.merge(targetId, score, Double::sum);
        }

        // Rank by RRF, then materialize top-N docs from Meili results (fallback)
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(rrf.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        ranked = ranked.subList(0, Math.min(size, ranked.size()));

        // Build id->doc map from Meili hits
        Map<String, Map<String, Object>> idToDoc = new HashMap<>();
        for (Map<String, Object> h : hits) idToDoc.put(String.valueOf(h.get("id")), h);

        List<Map<String, Object>> fusedHits = new ArrayList<>();
        for (Map.Entry<String, Double> e : ranked) {
            Map<String, Object> doc = idToDoc.get(e.getKey());
            if (doc != null) fusedHits.add(doc);
        }

        Map<String, Object> out = new LinkedHashMap<>(meili);
        out.put("hits", fusedHits);
        out.put("estimatedTotalHits", fusedHits.size());
        return out;
    }

    private Map<String, Object> buildVectorFilter(String filter) {
        // For now, only pre-filter in_stock=true when present; extensible later
        if (filter != null && filter.contains("in_stock = true")) {
            return Map.of("must", List.of(Map.of("key", "in_stock", "match", Map.of("value", true))));
        }
        return Map.of();
    }
}


