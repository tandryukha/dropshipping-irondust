package com.irondust.search.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FeatureFlagService {
    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);
    private final DatabaseClient db;

    public FeatureFlagService(DatabaseClient db) {
        this.db = db;
        ensureSchema().then(ensureDefaults()).subscribe();
    }

    private Mono<Void> ensureSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS feature_flags (name TEXT PRIMARY KEY, enabled BOOLEAN NOT NULL)";
        return db.sql(ddl).fetch().rowsUpdated().then();
    }

    private Mono<Void> ensureDefaults() {
        // Insert defaults if table is empty
        return db.sql("SELECT COUNT(1) AS c FROM feature_flags").fetch().one()
                .flatMap(row -> {
                    Number c = (Number) row.get("c");
                    if (c != null && c.longValue() == 0L) {
                        Map<String, Boolean> d = com.irondust.search.admin.FeatureFlagDefaults.defaults();
                        AtomicBoolean any = new AtomicBoolean(false);
                        return reactor.core.publisher.Flux.fromIterable(d.entrySet())
                                .flatMap(e -> db.sql("INSERT INTO feature_flags(name, enabled) VALUES(:name,:enabled)")
                                        .bind("name", e.getKey())
                                        .bind("enabled", e.getValue())
                                        .fetch().rowsUpdated())
                                .doOnNext(i -> any.set(true))
                                .then();
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> Mono.empty());
    }

    public Mono<Boolean> isEnabled(String name, boolean defaultValue) {
        return db.sql("SELECT enabled FROM feature_flags WHERE name = :name")
                .bind("name", name)
                .map(row -> (Boolean) row.get("enabled"))
                .one()
                .defaultIfEmpty(defaultValue)
                .onErrorResume(e -> {
                    log.warn("feature flag read failure: {}", e.toString());
                    return Mono.just(defaultValue);
                });
    }

    public Mono<Void> setEnabled(String name, boolean enabled) {
        return db.sql("INSERT INTO feature_flags(name, enabled) VALUES(:name,:enabled) ON CONFLICT (name) DO UPDATE SET enabled = EXCLUDED.enabled")
                .bind("name", name)
                .bind("enabled", enabled)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Map<String, Object>> allFlags() {
        return db.sql("SELECT name, enabled FROM feature_flags")
                .fetch().all()
                .collectMap(m -> String.valueOf(m.get("name")), m -> (Object) m.get("enabled"))
                .map(map -> (Map<String, Object>) map)
                .defaultIfEmpty(java.util.Map.of());
    }
}


