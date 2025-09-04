package com.irondust.search.service;

import com.irondust.search.config.AppProperties;
import com.irondust.search.model.ContentDoc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContentIndexService {
    private final WebClient meiliClient;
    private final AppProperties appProperties;

    public ContentIndexService(@Qualifier("meiliClient") WebClient meiliClient, AppProperties appProperties) {
        this.meiliClient = meiliClient;
        this.appProperties = appProperties;
    }

    private String index() {
        String idx = appProperties.getContentIndexName();
        return (idx == null || idx.isBlank()) ? "content" : idx;
    }

    public Mono<Void> ensureIndex() {
        Map<String, Object> payload = Map.of("uid", index(), "primaryKey", "id");
        return meiliClient.post().uri("/indexes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(e -> Mono.empty())
                .then(updateSettings());
    }

    public Mono<Void> updateSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("searchableAttributes", List.of("title", "excerpt", "topic", "tags"));
        settings.put("filterableAttributes", List.of("source", "license", "language", "topic", "tags"));
        settings.put("sortableAttributes", List.of("updatedAt"));
        return meiliClient.patch().uri("/indexes/{uid}/settings", index())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(settings)
                .retrieve()
                .bodyToMono(Map.class)
                .then();
    }

    public Mono<Void> upsert(List<ContentDoc> docs) {
        if (CollectionUtils.isEmpty(docs)) return Mono.empty();
        return meiliClient.post().uri("/indexes/{uid}/documents", index())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(docs))
                .retrieve()
                .bodyToMono(Map.class)
                .then();
    }

    public Mono<Map<String, Object>> search(String q, String filter, int page, int size) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("q", q == null ? "" : q);
        if (filter != null && !filter.isBlank()) payload.put("filter", filter);
        payload.put("page", page);
        payload.put("hitsPerPage", size);
        payload.put("facets", List.of("source", "license", "language", "topic", "tags"));
        return meiliClient.post().uri("/indexes/{uid}/search", index())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .onErrorResume(e -> Mono.just(Map.of("hits", List.of(), "estimatedTotalHits", 0)));
    }
}


