package com.irondust.search.controller;

import com.irondust.search.service.MeiliService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class HealthController {
    private final MeiliService meiliService;

    public HealthController(MeiliService meiliService) { this.meiliService = meiliService; }

    @GetMapping("/healthz")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return meiliService.isHealthy()
                .map(ok -> ok
                        ? ResponseEntity.ok(Map.<String, Object>of("ok", Boolean.TRUE))
                        : ResponseEntity.status(500).body(Map.<String, Object>of("ok", Boolean.FALSE)))
                .onErrorReturn(ResponseEntity.status(500).body(Map.<String, Object>of("ok", Boolean.FALSE)));
    }
}


