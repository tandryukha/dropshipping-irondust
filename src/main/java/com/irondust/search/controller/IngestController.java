package com.irondust.search.controller;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.IngestService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

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

    @PostMapping(value = "/full", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<IngestDtos.IngestReport>> ingestFull(@RequestHeader(value = "x-admin-key", required = false) String adminKey) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        return ingestService.ingestFull().map(ResponseEntity::ok);
    }

    @PostMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<IngestDtos.IngestReport>> ingestProducts(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @RequestBody IngestDtos.TargetedIngestRequest body) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        if (body == null || body.getIds() == null || body.getIds().isEmpty()) {
            IngestDtos.IngestReport empty = new IngestDtos.IngestReport();
            empty.setIndexed(0);
            empty.setConflicts_total(0);
            empty.setWarnings_total(0);
            empty.setProducts(java.util.List.of());
            return Mono.just(ResponseEntity.badRequest().body(empty));
        }
        return ingestService.ingestByIds(body.getIds())
                .map(ResponseEntity::ok);
    }

    // Removed SSE streaming endpoint
}



