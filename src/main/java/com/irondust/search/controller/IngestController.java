package com.irondust.search.controller;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.IngestService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.List;

import com.irondust.search.dto.IngestDtos;

@RestController
@RequestMapping("/ingest")
public class IngestController {
    private final IngestService ingestService;
    private final AppProperties appProperties;

    public IngestController(IngestService ingestService, AppProperties appProperties) {
        this.ingestService = ingestService;
        this.appProperties = appProperties;
    }

    @PostMapping("/full")
    public Mono<ResponseEntity<Map<String, Object>>> ingestFull(@RequestHeader(value = "x-admin-key", required = false) String adminKey) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).body(Map.of("error", "unauthorized")));
        }
        return ingestService.ingestFull().map(count -> ResponseEntity.ok(Map.of("indexed", count)));
    }

    @PostMapping("/products")
    public Mono<ResponseEntity<Map<String, Object>>> ingestProducts(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @RequestBody IngestDtos.TargetedIngestRequest body) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).body(Map.of("error", "unauthorized")));
        }
        if (body == null || body.getIds() == null || body.getIds().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "ids required")));
        }
        return ingestService.ingestByIds(body.getIds())
                .map(count -> ResponseEntity.ok(Map.of("indexed", count)));
    }
}



