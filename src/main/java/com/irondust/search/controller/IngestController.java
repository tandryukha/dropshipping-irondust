package com.irondust.search.controller;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.IngestService;
import com.irondust.search.service.TranslationService;
import com.irondust.search.service.enrichment.AIEnricher;
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
    private final TranslationService translationService;

    public IngestController(IngestService ingestService, AppProperties appProperties, TranslationService translationService) {
        this.ingestService = ingestService;
        this.appProperties = appProperties;
        this.translationService = translationService;
    }

    @PostMapping(value = "/full", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<IngestDtos.IngestReport>> ingestFull(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @RequestHeader(value = "x-clear-ai-cache", required = false) String clearAi,
            @RequestHeader(value = "x-clear-translation-cache", required = false) String clearTr) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        // Optional header-triggered cache clears
        if ("true".equalsIgnoreCase(clearAi)) {
            AIEnricher.clearPersistentCache();
        }
        if ("true".equalsIgnoreCase(clearTr)) {
            translationService.clearPersistentCache();
        }
        return ingestService.ingestFull().map(ResponseEntity::ok);
    }

    @PostMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<IngestDtos.IngestReport>> ingestProducts(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @RequestHeader(value = "x-clear-ai-cache", required = false) String clearAi,
            @RequestHeader(value = "x-clear-translation-cache", required = false) String clearTr,
            @RequestBody IngestDtos.TargetedIngestRequest body) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        if ("true".equalsIgnoreCase(clearAi)) {
            AIEnricher.clearPersistentCache();
        }
        if ("true".equalsIgnoreCase(clearTr)) {
            translationService.clearPersistentCache();
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



