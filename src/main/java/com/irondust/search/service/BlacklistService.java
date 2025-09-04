package com.irondust.search.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class BlacklistService {
    private static final Logger log = LoggerFactory.getLogger(BlacklistService.class);
    private final DatabaseClient db;

    public BlacklistService(DatabaseClient db) {
        this.db = db;
        ensureSchema().subscribe();
    }

    private Mono<Void> ensureSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS product_blacklist (" +
                "id TEXT PRIMARY KEY, " +
                "reason TEXT, " +
                "created_at TIMESTAMPTZ DEFAULT NOW()" +
                ")";
        return db.sql(ddl).fetch().rowsUpdated().then();
    }

    public Mono<Boolean> isBlacklistedId(String id) {
        if (id == null || id.isBlank()) return Mono.just(false);
        return db.sql("SELECT 1 FROM product_blacklist WHERE id = :id")
                .bind("id", id)
                .fetch().first()
                .map(m -> true)
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.warn("blacklist check failure: {}", e.toString());
                    return Mono.just(false);
                });
    }

    public Mono<Long> add(String id, String reason) {
        if (id == null || id.isBlank()) return Mono.just(0L);
        return db.sql("INSERT INTO product_blacklist(id, reason) VALUES(:id,:reason) " +
                        "ON CONFLICT (id) DO UPDATE SET reason = EXCLUDED.reason")
                .bind("id", id)
                .bind("reason", reason)
                .fetch().rowsUpdated()
                .onErrorResume(e -> {
                    log.error("failed to add id to blacklist id={}: {}", id, e.toString());
                    return Mono.just(0L);
                });
    }

    public Mono<Void> addAll(List<String> ids, String reason) {
        if (ids == null || ids.isEmpty()) return Mono.empty();
        return Flux.fromIterable(ids)
                .concatMap(id -> add(id, reason))
                .then();
    }

    public Mono<Long> remove(String id) {
        if (id == null || id.isBlank()) return Mono.just(0L);
        return db.sql("DELETE FROM product_blacklist WHERE id = :id")
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    public Flux<Map<String, Object>> listEntries() {
        return db.sql("SELECT id, reason, created_at FROM product_blacklist ORDER BY created_at DESC")
                .fetch().all()
                .map(m -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", String.valueOf(m.get("id")));
                    Object r = m.get("reason");
                    out.put("reason", r != null ? String.valueOf(r) : null);
                    Object ca = m.get("created_at");
                    out.put("created_at", ca != null ? String.valueOf(ca) : null);
                    return out;
                });
    }

    public Mono<List<String>> listIds() {
        return db.sql("SELECT id FROM product_blacklist ORDER BY created_at DESC")
                .fetch().all()
                .map(m -> String.valueOf(m.get("id")))
                .collectList();
    }
}


