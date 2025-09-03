package com.irondust.search.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class AiAnswerService {
    private static final Logger log = LoggerFactory.getLogger(AiAnswerService.class);
    private final MeiliService meiliService;

    public AiAnswerService(MeiliService meiliService) {
        this.meiliService = meiliService;
    }

    /**
     * Grounded short answer using top-k Meili results; this is a placeholder that returns an extractive
     * answer-like structure without calling an external LLM. Can be swapped to LLM later.
     */
    public Mono<Map<String, Object>> quickGroundedAnswer(String q, String filter, List<String> sort, int page, int size, List<String> facets) {
        int k = Math.min(20, Math.max(10, size));
        return meiliService.searchRaw(q, filter, sort, 1, k, facets)
                .map(raw -> summarize(q, raw));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summarize(String q, Map<String, Object> raw) {
        List<Map<String, Object>> hits = (List<Map<String, Object>>) raw.getOrDefault("hits", List.of());
        List<Map<String, Object>> top = new ArrayList<>();
        for (int i = 0; i < Math.min(5, hits.size()); i++) {
            Map<String, Object> h = hits.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.get("id"));
            m.put("name", h.get("name"));
            m.put("price_cents", h.get("price_cents"));
            m.put("price_per_serving", h.get("price_per_serving"));
            m.put("permalink", h.get("permalink"));
            top.add(m);
        }
        String guidance = buildGuidance(q, hits);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", guidance);
        out.put("items", top);
        return out;
    }

    private String buildGuidance(String q, List<Map<String, Object>> hits) {
        String qt = (q == null ? "" : q.toLowerCase());
        boolean cheap = qt.contains("cheap") || qt.contains("budget") || qt.contains("under ");
        boolean protein = qt.contains("protein") || qt.contains("whey") || qt.contains("isolate") || qt.contains("casein");
        if (cheap && protein) {
            return "Budget tip: sort by price per serving and check €/100g; here are a few good-value proteins.";
        }
        if (protein) {
            return "Here are protein options relevant to your query.";
        }
        return "Here are items relevant to your query.";
    }
}


