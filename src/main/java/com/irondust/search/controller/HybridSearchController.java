package com.irondust.search.controller;

import com.irondust.search.dto.SearchDtos;
import com.irondust.search.model.ProductDoc;
import com.irondust.search.service.FilterStringBuilder;
import com.irondust.search.service.HybridSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
public class HybridSearchController {
    private final HybridSearchService hybridSearchService;

    public HybridSearchController(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping("/search/hybrid")
    public Mono<SearchDtos.SearchResponseBody<ProductDoc>> search(@RequestBody SearchDtos.SearchRequestBody body) {
        Map<String, Object> filters = body.getFilters();
        if (filters == null) filters = new LinkedHashMap<>();
        if (!filters.containsKey("in_stock")) filters.put("in_stock", true);
        String filter = FilterStringBuilder.build(filters);
        List<String> facets = List.of("brand_slug", "categories_slugs", "form", "diet_tags", "goal_tags");

        return hybridSearchService.search(body.getQ(), filter, body.getSort(), body.getPage(), body.getSize(), facets)
                .map(raw -> SearchControllerMapper.mapToResponse(raw, body.getLang()));
    }
}


