package com.irondust.search.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.irondust.search.config.AppProperties;
import com.irondust.search.service.MeiliService;
import com.irondust.search.service.WooStoreService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin-only endpoints for inspecting raw product data.
 *
 * <p>Provides utilities to fetch:
 * <ul>
 *     <li>Raw product JSON directly from WooCommerce Store API</li>
 *     <li>Raw product document as stored in Meilisearch (our system)</li>
 * </ul>
 * Both endpoints require the {@code x-admin-key} header to match {@link AppProperties#getAdminKey()}.
 */
@RestController
@RequestMapping("/admin/raw")
public class AdminRawController {
    private final AppProperties appProperties;
    private final WooStoreService wooStoreService;
    private final MeiliService meiliService;

    public AdminRawController(AppProperties appProperties, WooStoreService wooStoreService, MeiliService meiliService) {
        this.appProperties = appProperties;
        this.wooStoreService = wooStoreService;
        this.meiliService = meiliService;
    }

    /**
     * Fetches raw product JSON directly from WooCommerce by numeric product id.
     * Accepts either a numeric id (e.g., {@code 31476}) or a string with {@code wc_} prefix (e.g., {@code wc_31476}).
     */
    @GetMapping(value = "/woo/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<JsonNode>> getWooRaw(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @PathVariable("id") String id) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        Long numericId = parseNumericId(id);
        if (numericId == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return wooStoreService.fetchProductById(numericId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * Fetches raw product document as stored in Meilisearch (our system).
     * Accepts either full id (e.g., {@code wc_31476}) or numeric id (e.g., {@code 31476}).
     */
    @GetMapping(value = "/system/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> getSystemRaw(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @PathVariable("id") String id) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        String docId = normalizeDocId(id);
        if (docId == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return meiliService.getDocumentRaw(docId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    private static Long parseNumericId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase();
        if (s.startsWith("wc_")) s = s.substring(3);
        if (s.startsWith("wc")) s = s.substring(2);
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeDocId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.startsWith("wc_")) return s;
        // Also accept forms like wc123 or plain numeric 123
        if (s.toLowerCase().startsWith("wc")) {
            String tail = s.substring(2);
            try {
                Long.parseLong(tail);
                return "wc_" + tail;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            Long.parseLong(s);
            return "wc_" + s;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}


