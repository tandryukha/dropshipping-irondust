package com.irondust.search.controller;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.BlacklistService;
import com.irondust.search.service.MeiliService;
import com.irondust.search.service.QdrantService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
@RequestMapping("/admin/blacklist")
public class BlacklistAdminController {
    private final BlacklistService blacklistService;
    private final AppProperties appProperties;
    private final MeiliService meiliService;
    private final QdrantService qdrantService;

    public BlacklistAdminController(BlacklistService blacklistService, AppProperties appProperties,
                                    MeiliService meiliService, QdrantService qdrantService) {
        this.blacklistService = blacklistService;
        this.appProperties = appProperties;
        this.meiliService = meiliService;
        this.qdrantService = qdrantService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> list() {
        return blacklistService.listEntries()
                .collectList()
                .map(list -> ResponseEntity.ok(Map.of("entries", list)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> add(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @RequestBody Map<String, Object> body) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        List<String> ids = extractIds(body);
        String reason = body != null ? String.valueOf(body.getOrDefault("reason", "manual")) : "manual";
        if (ids.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "ids required")));
        }
        List<String> docIds = normalizeDocIds(ids);
        return blacklistService.addAll(docIds, reason)
                .then(meiliService.deleteDocumentsByIds(docIds))
                .then(qdrantService.deleteByDocIds(docIds))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "ok", true,
                        "ids", docIds,
                        "action", "blacklisted_and_deindexed"
                )));
    }

    @DeleteMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> remove(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @PathVariable("id") String id) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        String docId = normalizeDocId(id);
        return blacklistService.remove(docId)
                .map(count -> ResponseEntity.ok(Map.of(
                        "ok", count > 0,
                        "id", docId,
                        "removed", count
                )));
    }

    private static List<String> extractIds(Map<String, Object> body) {
        if (body == null) return List.of();
        Object idsVal = body.get("ids");
        if (idsVal instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) if (o != null) out.add(String.valueOf(o));
            return out;
        }
        Object idVal = body.get("id");
        if (idVal != null) return List.of(String.valueOf(idVal));
        Object raw = body.get("raw");
        if (raw instanceof String s && !s.isBlank()) {
            String[] parts = s.split(",|\\n");
            List<String> out = new ArrayList<>();
            for (String p : parts) if (p != null && !p.isBlank()) out.add(p.trim());
            return out;
        }
        return List.of();
    }

    private static List<String> normalizeDocIds(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) out.add(normalizeDocId(id));
        return out;
    }

    private static String normalizeDocId(String id) {
        if (id == null) return null;
        String s = id.trim();
        if (s.matches("^[0-9]+$")) return "wc_" + s;
        return s;
    }
}


