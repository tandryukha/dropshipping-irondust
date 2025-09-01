package com.irondust.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irondust.search.config.AppProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class MeiliDocumentService {
    private final WebClient meiliClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public MeiliDocumentService(@Qualifier("meiliClient") WebClient meiliClient, AppProperties appProperties, ObjectMapper objectMapper) {
        this.meiliClient = meiliClient;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public Flux<Map<String, Object>> streamAllBasic() {
        String index = appProperties.getIndexName();
        int pageSize = 500;
        String fields = String.join(",",
                List.of(
                        "id","name","brand_name","categories_slugs","categories_names",
                        "ingredients_key","goal_tags","diet_tags","benefit_snippet",
                        "form","parent_id","in_stock","price","price_cents"
                )
        );
        return Flux.range(0, Integer.MAX_VALUE)
                .concatMap(page -> meiliClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/indexes/{uid}/documents")
                                .queryParam("limit", pageSize)
                                .queryParam("offset", page * pageSize)
                                .queryParam("fields", fields)
                                .build(index))
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(body -> {
                            try {
                                Map<String, Object> resp = objectMapper.readValue(body, new TypeReference<Map<String, Object>>(){});
                                Object resultsObj = resp.get("results");
                                List<Map<String, Object>> casted = new java.util.ArrayList<>();
                                if (resultsObj instanceof List<?> list) {
                                    for (Object o : list) if (o instanceof Map<?, ?> m) casted.add((Map<String, Object>) m);
                                }
                                return casted;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                )
                .takeWhile(list -> list != null && !list.isEmpty())
                .concatMap(Flux::fromIterable);
    }

    public Mono<Long> countDocuments() {
        String index = appProperties.getIndexName();
        return meiliClient.get()
                .uri("/indexes/{uid}/stats", index)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(resp -> {
                    Object n = resp.get("numberOfDocuments");
                    if (n instanceof Number num) return num.longValue();
                    return 0L;
                })
                .onErrorResume(e -> Mono.just(0L));
    }

    public Mono<Map<String, Object>> getDocumentRaw(String id) {
        String index = appProperties.getIndexName();
        return meiliClient.get().uri("/indexes/{uid}/documents/{id}", index, id)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}


