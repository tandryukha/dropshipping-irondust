package com.irondust.search.admin;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.FeatureFlagService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/admin/feature-flags")
public class FeatureFlagsController {
    private final FeatureFlagService flags;
    private final AppProperties appProperties;

    public FeatureFlagsController(FeatureFlagService flags, AppProperties appProperties) {
        this.flags = flags;
        this.appProperties = appProperties;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> list() {
        return flags.allFlags().map(ResponseEntity::ok);
    }

    @PatchMapping(path = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> set(
            @RequestHeader(value = "x-admin-key", required = false) String adminKey,
            @PathVariable String key,
            @RequestBody Map<String, Object> body) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        Object enabled = body.get("enabled");
        boolean val = enabled instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(enabled));
        return flags.setEnabled(key, val).then(flags.allFlags()).map(ResponseEntity::ok);
    }
}


