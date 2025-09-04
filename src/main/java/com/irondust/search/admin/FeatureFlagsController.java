package com.irondust.search.admin;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/admin/feature-flags")
public class FeatureFlagsController {
    private final FeatureFlagsService service;

    public FeatureFlagsController(FeatureFlagsService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Boolean>>> list() {
        return service.list().map(ResponseEntity::ok);
    }

    @PatchMapping(path = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Boolean>>> set(@PathVariable String key, @RequestBody Map<String, Object> body) {
        Object enabled = body.get("enabled");
        boolean val = enabled instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(enabled));
        return service.set(key, val).map(ResponseEntity::ok);
    }
}


