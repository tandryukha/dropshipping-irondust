package com.irondust.search.controller;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.EmbeddingService;
import com.irondust.search.service.VectorIndexService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vectors")
public class VectorController {
    private final VectorIndexService vectorIndexService;
    private final AppProperties appProperties;
    private final EmbeddingService embeddingService;

    public VectorController(VectorIndexService vectorIndexService, AppProperties appProperties, EmbeddingService embeddingService) {
        this.vectorIndexService = vectorIndexService;
        this.appProperties = appProperties;
        this.embeddingService = embeddingService;
    }

    @PostMapping("/reindex/all")
    public Mono<ResponseEntity<Map<String, Object>>> reindexAll(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @RequestParam(value = "batchSize", required = false, defaultValue = "100") int batchSize) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        if (!embeddingService.isEnabled()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "OPENAI_API_KEY not set; embeddings disabled")));
        }
        return vectorIndexService.reindexAll(batchSize)
                .thenReturn(ResponseEntity.ok(Map.of("status", "ok")));
    }

    @PostMapping("/reindex")
    public Mono<ResponseEntity<Map<String, Object>>> reindexByIds(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @RequestBody Map<String, Object> body,
            @RequestParam(value = "batchSize", required = false, defaultValue = "100") int batchSize) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        Object arr = body.get("ids");
        if (!(arr instanceof List<?> list) || list.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "ids required")));
        }
        List<String> ids = list.stream().map(String::valueOf).toList();
        if (!embeddingService.isEnabled()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "OPENAI_API_KEY not set; embeddings disabled", "count", ids.size())));
        }
        return vectorIndexService.reindexByIds(ids, batchSize)
                .thenReturn(ResponseEntity.ok(Map.of("status", "ok", "count", ids.size())));
    }
}


