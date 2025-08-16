package com.irondust.search.controller;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.IngestService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

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
}



