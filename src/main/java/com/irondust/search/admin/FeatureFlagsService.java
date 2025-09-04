package com.irondust.search.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irondust.search.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeatureFlagsService {
    private static final Logger log = LoggerFactory.getLogger(FeatureFlagsService.class);

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    public FeatureFlagsService(AppProperties appProperties) {
        this.appProperties = appProperties;
        tryLoad();
    }

    private Path getPath() {
        String path = appProperties.getFeatureFlagsPath();
        if (path == null || path.isBlank()) path = "tmp/feature-flags.json";
        return Path.of(path);
    }

    private synchronized void tryLoad() {
        try {
            Path p = getPath();
            if (Files.exists(p)) {
                Map<String, Boolean> m = objectMapper.readValue(p.toFile(), new TypeReference<>() {});
                cache.clear();
                cache.putAll(m);
            }
        } catch (Exception e) {
            log.warn("Failed to load feature flags: {}", e.toString());
        }
    }

    private synchronized void persist() {
        try {
            Path p = getPath();
            Files.createDirectories(p.getParent());
            Map<String, Boolean> ordered = new LinkedHashMap<>(cache);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), ordered);
        } catch (Exception e) {
            log.warn("Failed to persist feature flags: {}", e.toString());
        }
    }

    public Mono<Map<String, Boolean>> list() {
        return Mono.fromSupplier(() -> Collections.unmodifiableMap(new LinkedHashMap<>(cache)));
    }

    public Mono<Map<String, Boolean>> set(String key, boolean enabled) {
        return Mono.fromRunnable(() -> {
            cache.put(key, enabled);
            persist();
        }).then(list());
    }
}


