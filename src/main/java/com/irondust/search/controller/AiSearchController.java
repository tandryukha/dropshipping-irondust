package com.irondust.search.controller;

import com.irondust.search.dto.SearchDtos;
import com.irondust.search.service.AiAnswerService;
import com.irondust.search.service.FeatureFlagService;
import com.irondust.search.service.FilterStringBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
public class AiSearchController {
    private static final String FLAG_AI_SEARCH = "ai_search";
    private final AiAnswerService aiAnswerService;
    private final FeatureFlagService flags;

    public AiSearchController(AiAnswerService aiAnswerService, FeatureFlagService flags) {
        this.aiAnswerService = aiAnswerService;
        this.flags = flags;
    }

    @PostMapping("/search/ai")
    public Mono<Map<String, Object>> ai(@RequestBody SearchDtos.SearchRequestBody body) {
        Map<String, Object> filters = body.getFilters();
        if (filters == null) filters = new LinkedHashMap<>();
        if (!filters.containsKey("in_stock")) filters.put("in_stock", true);
        String filter = FilterStringBuilder.build(filters);
        List<String> facets = List.of("brand_slug", "categories_slugs", "form", "diet_tags", "goal_tags");
        List<String> sort = body.getSort();
        return flags.isEnabled(FLAG_AI_SEARCH, true).flatMap(enabled -> {
            if (!enabled) {
                return Mono.just(Map.of(
                        "answer", "AI is disabled",
                        "items", List.of()
                ));
            }
            return aiAnswerService.quickGroundedAnswer(body.getQ(), filter, sort, body.getPage(), body.getSize(), facets);
        });
    }
}


