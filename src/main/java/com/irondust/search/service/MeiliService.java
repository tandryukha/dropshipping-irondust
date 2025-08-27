package com.irondust.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irondust.search.config.AppProperties;
import com.irondust.search.model.ProductDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MeiliService {
    private static final Logger log = LoggerFactory.getLogger(MeiliService.class);

    private final WebClient meiliClient;
    // ObjectMapper is currently unused but retained for potential JSON transformations
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public MeiliService(@Qualifier("meiliClient") WebClient meiliClient, ObjectMapper objectMapper, AppProperties appProperties) {
        this.meiliClient = meiliClient;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public Mono<Void> ensureIndexWithSettings(List<String> filterableAttrs, List<String> sortableAttrs, List<String> searchableAttrs) {
        String index = appProperties.getIndexName();
        Map<String, Object> indexPayload = Map.of("uid", index, "primaryKey", "id");
        return meiliClient.post().uri("/indexes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(indexPayload)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(err -> {
                    // likely already exists
                    return Mono.empty();
                })
                .then(updateSettings(filterableAttrs, sortableAttrs, searchableAttrs));
    }

    public Mono<Void> updateSettings(List<String> filterableAttrs, List<String> sortableAttrs, List<String> searchableAttrs) {
        String index = appProperties.getIndexName();
        Map<String, Object> settings = new HashMap<>();
        if (!CollectionUtils.isEmpty(searchableAttrs)) settings.put("searchableAttributes", searchableAttrs);
        if (!CollectionUtils.isEmpty(filterableAttrs)) settings.put("filterableAttributes", filterableAttrs);
        if (!CollectionUtils.isEmpty(sortableAttrs)) settings.put("sortableAttributes", sortableAttrs);
        // Phase 1: enable distinct attribute for variation grouping
        settings.put("distinctAttribute", "parent_id");
        return meiliClient.patch().uri("/indexes/{uid}/settings", index)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(settings)
                .retrieve()
                .bodyToMono(Map.class)
                .then();
    }

    public Mono<Void> addOrReplaceDocuments(List<ProductDoc> documents) {
        String index = appProperties.getIndexName();
        return meiliClient.post().uri("/indexes/{uid}/documents", index)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(documents))
                .retrieve()
                .bodyToMono(Map.class)
                .then();
    }

    public Mono<Map<String, Object>> searchRaw(String q, String filter, List<String> sort, int page, int size, List<String> facets) {
        String index = appProperties.getIndexName();
        Map<String, Object> payload = new HashMap<>();
        // Meilisearch requires 'q' in the payload; use empty string for filter-only searches
        payload.put("q", q == null ? "" : q);
        if (filter != null && !filter.isBlank()) payload.put("filter", filter);
        if (sort != null && !sort.isEmpty()) payload.put("sort", sort);
        payload.put("page", page);
        payload.put("hitsPerPage", size);
        if (facets != null && !facets.isEmpty()) payload.put("facets", facets);

        return meiliClient.post().uri("/indexes/{uid}/search", index)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .onErrorResume(e -> {
                    // Fallback: retry without sort (e.g., when field isn't sortable)
                    log.warn("Meili search error (will retry without sort): {}", e.toString());
                    Map<String, Object> retryPayload = new HashMap<>(payload);
                    retryPayload.remove("sort");
                    return meiliClient.post().uri("/indexes/{uid}/search", index)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(retryPayload)
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                            .onErrorResume(e2 -> {
                                log.error("Meili search error on retry", e2);
                                return Mono.just(Map.of("hits", List.of(), "estimatedTotalHits", 0));
                            });
                });
    }

    public Mono<Map<String, Object>> getDocumentRaw(String id) {
        String index = appProperties.getIndexName();
        return meiliClient.get().uri("/indexes/{uid}/documents/{id}", index, id)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public Mono<Boolean> isHealthy() {
        return meiliClient.get().uri("/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> true)
                .onErrorReturn(false);
    }
}


