package com.irondust.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irondust.search.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class WooStoreService {
    private static final Logger log = LoggerFactory.getLogger(WooStoreService.class);

    private final WebClient wooClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public WooStoreService(@Qualifier("wooClient") WebClient wooClient, ObjectMapper objectMapper, AppProperties appProperties) {
        this.wooClient = wooClient;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public Flux<JsonNode> paginateProducts() {
        int perPage = appProperties.getPerPage();
        return Flux.create(sink -> {
            fetchPage(1, perPage, sink);
        });
    }

    private void fetchPage(int page, int perPage, reactor.core.publisher.FluxSink<JsonNode> sink) {
        wooClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/wp-json/wc/store/v1/products")
                        .queryParam("per_page", perPage)
                        .queryParam("page", page)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .subscribe(json -> {
                    if (json.isArray() && json.size() > 0) {
                        for (JsonNode node : json) {
                            sink.next(node);
                        }
                        fetchPage(page + 1, perPage, sink);
                    } else {
                        sink.complete();
                    }
                }, err -> {
                    log.error("Error fetching products page {}", page, err);
                    sink.complete();
                });
    }
}


