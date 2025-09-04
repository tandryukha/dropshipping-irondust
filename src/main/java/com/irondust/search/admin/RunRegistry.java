package com.irondust.search.admin;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunRegistry {
    public static class RunInfo {
        public String runId;
        public String type; // ingest | index
        public String status; // queued|running|completed|failed
        public int processed;
        public Integer total;
        public Instant startedAt;
        public Instant updatedAt;
        public Instant endedAt;
        public String message;
    }

    private final Map<String, RunInfo> runs = new ConcurrentHashMap<>();

    public void put(RunInfo info) {
        if (info != null && info.runId != null) {
            info.updatedAt = Instant.now();
            runs.put(info.runId, info);
        }
    }

    public RunInfo get(String runId) {
        return runs.get(runId);
    }

    public RunInfo latestOfType(String type) {
        return runs.values().stream()
                .filter(r -> type == null || type.equals(r.type))
                .sorted((a, b) -> b.updatedAt.compareTo(a.updatedAt))
                .findFirst().orElse(null);
    }
}


