package com.irondust.search.controller;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.ContentIndexService;
import com.irondust.search.service.ContentIngestService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class ContentController {
    private final ContentIndexService contentIndexService;
    private final ContentIngestService contentIngestService;
    private final AppProperties appProperties;

    public ContentController(ContentIndexService contentIndexService, ContentIngestService contentIngestService, AppProperties appProperties) {
        this.contentIndexService = contentIndexService;
        this.contentIngestService = contentIngestService;
        this.appProperties = appProperties;
    }

    @PostMapping(value = "/ingest/content/minimal", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> ingestMinimal(@RequestHeader(value = "x-admin-key", required = false) String adminKey) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        return contentIngestService.ingestMinimalSeed()
                .map(count -> ResponseEntity.ok(Map.of("indexed", count)));
    }

    @PostMapping(value = "/content/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String q = body == null ? "" : String.valueOf(body.getOrDefault("q", ""));
        String filter = body == null ? null : (String) body.get("filter");
        int page = body != null && body.get("page") instanceof Number ? ((Number) body.get("page")).intValue() : 1;
        int size = body != null && body.get("size") instanceof Number ? ((Number) body.get("size")).intValue() : 10;
        return contentIndexService.search(q, filter, page, size);
    }
}


