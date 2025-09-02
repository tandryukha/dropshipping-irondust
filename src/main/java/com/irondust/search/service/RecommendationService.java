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

    /**
     * Complements are items that pair well with the origin product but are not direct substitutes.
     * Heuristics:
     *  - Always in stock
     *  - Exclude same product and same variation group
     *  - Prefer different form OR different primary category from the origin
     *  - Prefer items sharing at least one goal tag with the origin
     *  - Sorted by rating and review_count for quality
     */
    @SuppressWarnings("unchecked")
    public Mono<SearchDtos.SearchResponseBody<ProductDoc>> complementsForProduct(String productId, String lang, int limit) {
        int fetchSize = Math.max(limit * 5, Math.max(24, limit));

        return meiliService.getDocumentRaw(productId)
                .defaultIfEmpty(Map.of())
                .flatMap(origin -> {
                    String originId = String.valueOf(origin.getOrDefault("id", productId));
                    String originParent = origin.get("parent_id") instanceof String s ? s : null;
                    String originForm = origin.get("form") instanceof String s ? s : null;
                    List<String> originCats = origin.get("categories_slugs") instanceof List<?> l ? (List<String>) (List<?>) l : List.of();
                    List<String> originGoals = origin.get("goal_tags") instanceof List<?> l ? (List<String>) (List<?>) l : List.of();

                    Map<String, Object> filters = new LinkedHashMap<>();
                    filters.put("in_stock", true);
                    // Do not constrain by category to allow cross-category pairings

                    // Pull a larger sample of popular items, then refine in-process
                    return meiliService.searchRaw("", FilterStringBuilder.build(filters), List.of("rating:desc", "review_count:desc"), 1, fetchSize, null)
                            .map(raw -> {
                                Object hitsObj = raw.get("hits");
                                List<Map<String, Object>> hits = new ArrayList<>();
                                if (hitsObj instanceof List<?> list) {
                                    for (Object h : list) {
                                        if (h instanceof Map<?, ?> m) hits.add((Map<String, Object>) m);
                                    }
                                }

                                // Filter to ensure complementarity
                                List<Map<String, Object>> filtered = new ArrayList<>();
                                for (Map<String, Object> doc : hits) {
                                    if (doc == null) continue;
                                    String id = String.valueOf(doc.get("id"));
                                    if (originId.equals(id)) continue;
                                    String pid = doc.get("parent_id") instanceof String s ? s : null;
                                    if (originParent != null && originParent.equals(pid)) continue;

                                    // Different form OR different category required
                                    String form = doc.get("form") instanceof String fs ? fs : null;
                                    List<String> cats = doc.get("categories_slugs") instanceof List<?> l2 ? (List<String>) (List<?>) l2 : List.of();
                                    boolean differentForm = originForm == null || form == null || !originForm.equals(form);
                                    boolean differentCategory = originCats.isEmpty() || cats.isEmpty() || Collections.disjoint(originCats, cats);
                                    if (!(differentForm || differentCategory)) continue;

                                    filtered.add(doc);
                                }

                                // Re-rank: boost items sharing goal tags
                                if (!originGoals.isEmpty()) {
                                    filtered.sort((a, b) -> {
                                        List<String> ga = a.get("goal_tags") instanceof List<?> la ? (List<String>) (List<?>) la : List.of();
                                        List<String> gb = b.get("goal_tags") instanceof List<?> lb ? (List<String>) (List<?>) lb : List.of();
                                        boolean sa = !Collections.disjoint(originGoals, ga);
                                        boolean sb = !Collections.disjoint(originGoals, gb);
                                        if (sa == sb) return 0; // keep original order otherwise
                                        return sa ? -1 : 1; // items sharing goals first
                                    });
                                }

                                // Truncate
                                if (filtered.size() > limit) {
                                    filtered = filtered.subList(0, limit);
                                }

                                Map<String, Object> respRaw = new LinkedHashMap<>();
                                respRaw.put("hits", filtered);
                                respRaw.put("estimatedTotalHits", filtered.size());
                                return SearchControllerMapper.mapToResponse(respRaw, lang);
                            });
                });
    }
}


