package com.irondust.search.service.content;

import com.irondust.search.model.ContentDoc;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches FDA enforcement reports (recalls) via openFDA API for dietary supplements.
 * API: https://api.fda.gov/food/enforcement.json
 */
@Service
public class FdaRecallFetcher {
    private final WebClient http;

    public FdaRecallFetcher() {
        this.http = WebClient.builder()
                .baseUrl("https://api.fda.gov")
                .defaultHeaders(h -> h.setAccept(MediaType.parseMediaTypes("application/json")))
                .build();
    }

    public Flux<ContentDoc> fetchRecent(int limit) {
        int capped = Math.max(1, Math.min(100, limit));
        // Query dietary supplement recalls; openFDA doesn't have a perfect filter, use product_description search
        String search = "search=product_description:%22dietary%20supplement%22&limit=" + capped;
        return http.get()
                .uri("/food/enforcement.json?" + search)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                .flatMapMany((java.util.Map<String, Object> map) -> {
                    Object res = map.getOrDefault("results", List.of());
                    List<java.util.Map<String, Object>> results;
                    if (res instanceof List<?> list) {
                        results = new ArrayList<>();
                        for (Object o : list) {
                            if (o instanceof java.util.Map<?,?> m) {
                                java.util.Map<String, Object> casted = new java.util.HashMap<>();
                                for (var e : m.entrySet()) {
                                    casted.put(String.valueOf(e.getKey()), e.getValue());
                                }
                                results.add(casted);
                            }
                        }
                    } else {
                        results = List.of();
                    }
                    List<ContentDoc> docs = new ArrayList<>();
                    for (Map<String, Object> r : results) {
                        ContentDoc d = new ContentDoc();
                        String id = String.valueOf(r.getOrDefault("recall_number", String.valueOf(r.hashCode())));
                        String title = String.valueOf(r.getOrDefault("product_description", "FDA Recall"));
                        String reason = String.valueOf(r.getOrDefault("reason_for_recall", ""));
                        String status = String.valueOf(r.getOrDefault("status", ""));
                        String dateStr = String.valueOf(r.getOrDefault("recall_initiation_date", ""));
                        OffsetDateTime dt = parseFdaDate(dateStr);
                        String link = "https://www.fda.gov/safety/recalls-market-withdrawals-safety-alerts";

                        String safeId = ("fda_" + id).replaceAll("[^a-zA-Z0-9_-]", "_");
                        d.setId(safeId);
                        d.setSource("fda");
                        d.setLicense("Public Domain");
                        d.setTitle(title);
                        d.setExcerpt((reason == null ? "" : reason) + (status == null || status.isBlank() ? "" : " (" + status + ")"));
                        d.setUrl(link);
                        d.setLanguage("en");
                        d.setTags(List.of("recall", "safety", "fda"));
                        d.setUpdatedAt(dt == null ? OffsetDateTime.now() : dt);
                        docs.add(d);
                    }
                    return Flux.fromIterable(docs);
                });
    }

    private OffsetDateTime parseFdaDate(String yyyymmdd) {
        try {
            if (yyyymmdd == null || yyyymmdd.length() != 8) return null;
            String iso = yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-" + yyyymmdd.substring(6, 8);
            return OffsetDateTime.parse(iso + "T00:00:00Z");
        } catch (Exception ignore) {
            return null;
        }
    }
}


