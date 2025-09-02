package com.irondust.search.service;

import com.irondust.search.controller.SearchControllerMapper;
import com.irondust.search.dto.SearchDtos;
import com.irondust.search.model.ProductDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final QdrantService qdrantService;
    private final MeiliService meiliService;

    public RecommendationService(QdrantService qdrantService, MeiliService meiliService) {
        this.qdrantService = qdrantService;
        this.meiliService = meiliService;
    }

    public Mono<SearchDtos.SearchResponseBody<ProductDoc>> alternativesForProduct(String productId, String lang, int limit) {
        int recLimit = Math.max(limit * 3, limit);
        Map<String, Object> filter = Map.of(
                "must", List.of(Map.of("key", "in_stock", "match", Map.of("value", true)))
        );

        return meiliService.getDocumentRaw(productId)
                .defaultIfEmpty(Map.of())
                .flatMap(origin -> qdrantService.recommendByDocId(productId, filter, recLimit)
                        .onErrorResume(e -> {
                            log.warn("Qdrant recommend failed for id={}: {}", productId, e.toString());
                            return Mono.just(List.of());
                        })
                        .flatMap(results -> {
                            // Extract original parent for sibling filtering
                            String originParent = origin.get("parent_id") instanceof String s ? s : null;
                            String originId = String.valueOf(origin.getOrDefault("id", productId));

                            // Preserve order from vector recs, map to doc ids via payload.doc_id
                            List<String> ids = new ArrayList<>();
                            for (QdrantService.SearchResult r : results) {
                                Object pid = r.payload != null ? r.payload.get("doc_id") : null;
                                if (pid == null) pid = r.payload != null ? r.payload.get("id") : null;
                                if (pid != null) ids.add(String.valueOf(pid));
                            }

                            // Dedup and cut
                            LinkedHashSet<String> dedup = new LinkedHashSet<>(ids);
                            List<String> take = new ArrayList<>(dedup);
                            if (take.size() > recLimit) take = take.subList(0, recLimit);

                            return Flux.fromIterable(take)
                                    .concatMap(meiliService::getDocumentRaw)
                                    .filter(doc -> {
                                        if (doc == null) return false;
                                        // Exclude the same product and same variation group
                                        String id = String.valueOf(doc.get("id"));
                                        if (originId.equals(id)) return false;
                                        String pid = doc.get("parent_id") instanceof String s ? s : null;
                                        if (originParent != null && originParent.equals(pid)) return false;
                                        // Keep in-stock only
                                        Object inStockObj = doc.get("in_stock");
                                        if (inStockObj instanceof Boolean b && !b) return false;
                                        return true;
                                    })
                                    .take(limit)
                                    .collectList();
                        })
                        .map(docs -> {
                            Map<String, Object> raw = new LinkedHashMap<>();
                            raw.put("hits", docs);
                            raw.put("estimatedTotalHits", docs.size());
                            return SearchControllerMapper.mapToResponse(raw, lang);
                        })
                );
    }
}


