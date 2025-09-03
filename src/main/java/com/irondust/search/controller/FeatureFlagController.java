package com.irondust.search.controller;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.FeatureFlagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class FeatureFlagController {
    private final FeatureFlagService flags;
    private final AppProperties appProperties;

    public FeatureFlagController(FeatureFlagService flags, AppProperties appProperties) {
        this.flags = flags; this.appProperties = appProperties;
    }

    @GetMapping("/feature-flags")
    public Mono<Map<String, Object>> getAll() { return flags.allFlags(); }

    @GetMapping("/feature-flags/{name}")
    public Mono<Map<String, Object>> getOne(@PathVariable String name, @RequestParam(defaultValue = "false") boolean defaultValue) {
        return flags.isEnabled(name, defaultValue).map(v -> Map.of("name", name, "enabled", v));
    }

    @PostMapping("/feature-flags/{name}")
    public Mono<ResponseEntity<Map<String, Object>>> setOne(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @PathVariable String name, @RequestParam boolean enabled) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        return flags.setEnabled(name, enabled).thenReturn(ResponseEntity.ok(Map.of("name", name, "enabled", enabled)));
    }
}


