package com.irondust.search.admin;

import com.irondust.search.service.IngestService;
import com.irondust.search.service.enrichment.AIEnricher;
import com.irondust.search.service.TranslationService;
import com.irondust.search.service.VectorIndexService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final IngestService ingestService;
    private final VectorIndexService vectorIndexService;
    private final TranslationService translationService;
    private final RunRegistry runRegistry;
    private final LogSseService logSseService;

    public AdminController(IngestService ingestService,
                           VectorIndexService vectorIndexService,
                           TranslationService translationService,
                           RunRegistry runRegistry,
                           LogSseService logSseService) {
        this.ingestService = ingestService;
        this.vectorIndexService = vectorIndexService;
        this.translationService = translationService;
        this.runRegistry = runRegistry;
        this.logSseService = logSseService;
    }

    @PostMapping(path = "/ingest/reingest", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> triggerReingest(
            @RequestBody(required = false) Map<String, Object> body) {
        String runId = UUID.randomUUID().toString();
        RunRegistry.RunInfo info = new RunRegistry.RunInfo();
        info.runId = runId;
        info.type = "ingest";
        info.status = "running";
        info.startedAt = Instant.now();
        info.updatedAt = Instant.now();
        runRegistry.put(info);

        boolean clearAi = body != null && Boolean.TRUE.equals(body.get("clearAiCache"));
        boolean clearTr = body != null && Boolean.TRUE.equals(body.get("clearTranslationCache"));
        if (clearAi) AIEnricher.clearPersistentCache();
        if (clearTr) translationService.clearPersistentCache();

        ingestService.ingestFull()
                .doOnSubscribe(s -> logSseService.append(runId, "Ingest started"))
                .doOnError(e -> {
                    info.status = "failed";
                    info.endedAt = Instant.now();
                    info.message = e.toString();
                    runRegistry.put(info);
                    logSseService.append(runId, "ERROR: " + e);
                })
                .doOnSuccess(report -> {
                    info.status = "completed";
                    info.endedAt = Instant.now();
                    info.processed = report.getIndexed();
                    // Persist report payload for later retrieval
                    try {
                        String p = persistRunResult("ingest", runId, report);
                        info.resultPath = p;
                    } catch (Exception ex) {
                        info.message = (info.message == null ? "" : info.message + "; ") + ("persist failed: " + ex);
                    }
                    runRegistry.put(info);
                    logSseService.append(runId, "Completed. Indexed=" + report.getIndexed());
                })
                .subscribe();

        return Mono.just(ResponseEntity.ok(Map.of("runId", runId, "type", "ingest", "status", info.status)));
    }

    @PostMapping(path = "/index/reindex", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> triggerReindex(@RequestParam(value = "batchSize", required = false, defaultValue = "100") int batchSize) {
        String runId = UUID.randomUUID().toString();
        RunRegistry.RunInfo info = new RunRegistry.RunInfo();
        info.runId = runId;
        info.type = "index";
        info.status = "running";
        info.startedAt = Instant.now();
        info.updatedAt = Instant.now();
        runRegistry.put(info);

        vectorIndexService.reindexAll(batchSize)
                .doOnSubscribe(s -> logSseService.append(runId, "Reindex started"))
                .doOnError(e -> {
                    info.status = "failed";
                    info.endedAt = Instant.now();
                    info.message = e.toString();
                    runRegistry.put(info);
                    logSseService.append(runId, "ERROR: " + e);
                })
                .doOnSuccess(v -> {
                    info.status = "completed";
                    info.endedAt = Instant.now();
                    // Minimal report for reindex
                    try {
                        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                        payload.put("runId", runId);
                        payload.put("type", "index");
                        payload.put("status", "completed");
                        payload.put("endedAt", info.endedAt != null ? info.endedAt.toString() : null);
                        String p = persistRunResult("index", runId, payload);
                        info.resultPath = p;
                    } catch (Exception ex) {
                        info.message = (info.message == null ? "" : info.message + "; ") + ("persist failed: " + ex);
                    }
                    runRegistry.put(info);
                    logSseService.append(runId, "Completed reindex");
                })
                .subscribe();

        return Mono.just(ResponseEntity.ok(Map.of("runId", runId, "type", "index", "status", info.status)));
    }

    @GetMapping(path = "/runs/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> latest(@RequestParam(value = "type", required = false) String type) {
        RunRegistry.RunInfo r = runRegistry.latestOfType(type);
        if (r == null) return Mono.just(ResponseEntity.ok(Map.of()));
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("runId", r.runId);
        m.put("type", r.type);
        m.put("status", r.status);
        m.put("processed", r.processed);
        m.put("total", r.total);
        m.put("startedAt", r.startedAt != null ? r.startedAt.toString() : null);
        m.put("updatedAt", r.updatedAt != null ? r.updatedAt.toString() : null);
        m.put("endedAt", r.endedAt != null ? r.endedAt.toString() : null);
        m.put("message", r.message);
        m.put("resultPath", r.resultPath);
        return Mono.just(ResponseEntity.ok(m));
    }

    @GetMapping(path = "/runs/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> run(@PathVariable String runId) {
        RunRegistry.RunInfo r = runRegistry.get(runId);
        if (r == null) return Mono.just(ResponseEntity.notFound().build());
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("runId", r.runId);
        m.put("type", r.type);
        m.put("status", r.status);
        m.put("processed", r.processed);
        m.put("total", r.total);
        m.put("startedAt", r.startedAt != null ? r.startedAt.toString() : null);
        m.put("updatedAt", r.updatedAt != null ? r.updatedAt.toString() : null);
        m.put("endedAt", r.endedAt != null ? r.endedAt.toString() : null);
        m.put("message", r.message);
        m.put("resultPath", r.resultPath);
        return Mono.just(ResponseEntity.ok(m));
    }

    @GetMapping(path = "/runs/{runId}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> logs(@PathVariable String runId) {
        return logSseService.stream(runId);
    }

    @GetMapping(path = "/runs/{runId}/result", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> runResult(@PathVariable String runId) {
        RunRegistry.RunInfo r = runRegistry.get(runId);
        if (r == null || r.resultPath == null || r.resultPath.isBlank()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        try {
            Path p = Paths.get(r.resultPath);
            if (!Files.exists(p)) return Mono.just(ResponseEntity.notFound().build());
            ObjectMapper om = new ObjectMapper();
            Object obj = om.readValue(p.toFile(), Object.class);
            return Mono.just(ResponseEntity.ok(obj));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.status(500).body(Map.of("error", e.toString())));
        }
    }

    @GetMapping(path = "/runs/latest/result", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> latestResult(@RequestParam(value = "type", required = false) String type) {
        RunRegistry.RunInfo r = runRegistry.latestOfType(type);
        if (r == null) return Mono.just(ResponseEntity.ok(Map.of()));
        return runResult(r.runId);
    }

    private String persistRunResult(String type, String runId, Object payload) throws Exception {
        String baseDir = "tmp/admin-runs";
        Path dir = Paths.get(baseDir, type);
        Files.createDirectories(dir);
        Path out = dir.resolve(runId + ".json");
        ObjectMapper om = new ObjectMapper();
        om.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), payload);
        return out.toAbsolutePath().toString();
    }
}


