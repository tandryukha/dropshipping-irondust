package com.irondust.search.controller;

import com.irondust.search.dto.SearchDtos;
import com.irondust.search.model.ProductDoc;
import com.irondust.search.service.FilterStringBuilder;
import com.irondust.search.service.MeiliService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
public class SearchController {
    private final MeiliService meiliService;

    public SearchController(MeiliService meiliService) {
        this.meiliService = meiliService;
    }

    @PostMapping("/search")
    public Mono<SearchDtos.SearchResponseBody<ProductDoc>> search(@Valid @RequestBody SearchDtos.SearchRequestBody body) {
        String filter = FilterStringBuilder.build(body.getFilters());
        List<String> facets = List.of("brand_slug", "categories_slugs", "form", "diet_tags", "goal_tags");
        return meiliService.searchRaw(body.getQ(), filter, body.getSort(), body.getPage(), body.getSize(), facets)
                .map(raw -> {
                    SearchDtos.SearchResponseBody<ProductDoc> resp = new SearchDtos.SearchResponseBody<>();
                    List<ProductDoc> items = new ArrayList<>();
                    Object hitsObj = raw.get("hits");
                    if (hitsObj instanceof List<?> hits) {
                        for (Object h : hits) {
                            if (h instanceof Map<?, ?> m) {
                                ProductDoc d = new ProductDoc();
                                mapToProductDoc(m, d);
                                items.add(d);
                            }
                        }
                    }
                    resp.setItems(items);
                    Object totalObj = raw.containsKey("totalHits") ? raw.get("totalHits") : raw.getOrDefault("estimatedTotalHits", 0);
                    long total = (totalObj instanceof Number) ? ((Number) totalObj).longValue() : 0L;
                    resp.setTotal(total);

                    Map<String, Map<String, Integer>> facetsDist = new LinkedHashMap<>();
                    Object facetsObj = raw.get("facetDistribution");
                    if (facetsObj instanceof Map<?, ?> f) {
                        for (String key : Arrays.asList("brand_slug", "categories_slugs", "form", "diet_tags", "goal_tags")) {
                            Object dmap = f.get(key);
                            if (dmap instanceof Map<?, ?> dm) {
                                Map<String, Integer> inner = new LinkedHashMap<>();
                                for (Map.Entry<?, ?> e : dm.entrySet()) {
                                    inner.put(String.valueOf(e.getKey()), ((Number) e.getValue()).intValue());
                                }
                                facetsDist.put(key, inner);
                            }
                        }
                    }
                    resp.setFacets(facetsDist);
                    return resp;
                });
    }

    private void mapToProductDoc(Map<?, ?> m, ProductDoc d) {
        d.setId((String) m.get("id"));
        d.setParent_id((String) m.get("parent_id"));
        d.setType((String) m.get("type"));
        d.setSku((String) m.get("sku"));
        d.setSlug((String) m.get("slug"));
        d.setName((String) m.get("name"));
        d.setPermalink((String) m.get("permalink"));
        d.setPrice_cents((m.get("price_cents") instanceof Number) ? ((Number) m.get("price_cents")).intValue() : null);
        d.setCurrency((String) m.get("currency"));
        d.setIn_stock((m.get("in_stock") instanceof Boolean) ? ((Boolean) m.get("in_stock")) : null);
        d.setLow_stock_remaining((m.get("low_stock_remaining") instanceof Number) ? ((Number) m.get("low_stock_remaining")).intValue() : null);
        d.setRating((m.get("rating") instanceof Number) ? ((Number) m.get("rating")).doubleValue() : null);
        d.setReview_count((m.get("review_count") instanceof Number) ? ((Number) m.get("review_count")).intValue() : null);
        d.setImages((List<String>) m.get("images"));
        d.setCategories_ids((List<Integer>) m.get("categories_ids"));
        d.setCategories_slugs((List<String>) m.get("categories_slugs"));
        d.setCategories_names((List<String>) m.get("categories_names"));
        d.setBrand_slug((String) m.get("brand_slug"));
        d.setBrand_name((String) m.get("brand_name"));
        d.setSearch_text((String) m.get("search_text"));
        // phase 1 fields
        d.setForm((String) m.get("form"));
        d.setFlavor((String) m.get("flavor"));
        d.setNet_weight_g((m.get("net_weight_g") instanceof Number)? ((Number) m.get("net_weight_g")).doubleValue() : null);
        d.setServings((m.get("servings") instanceof Number)? ((Number) m.get("servings")).intValue() : null);
        d.setServing_size_g((m.get("serving_size_g") instanceof Number)? ((Number) m.get("serving_size_g")).doubleValue() : null);
        d.setPrice((m.get("price") instanceof Number)? ((Number) m.get("price")).doubleValue() : null);
        d.setPrice_per_serving((m.get("price_per_serving") instanceof Number)? ((Number) m.get("price_per_serving")).doubleValue() : null);
        d.setPrice_per_100g((m.get("price_per_100g") instanceof Number)? ((Number) m.get("price_per_100g")).doubleValue() : null);
        d.setGoal_tags((List<String>) m.get("goal_tags"));
        d.setDiet_tags((List<String>) m.get("diet_tags"));
        d.setIngredients_key((List<String>) m.get("ingredients_key"));
        Object bs = m.get("benefit_snippet");
        if (bs instanceof String s) { d.setBenefit_snippet(s); }
        Object fq = m.get("faq");
        if (fq instanceof List<?> l) { d.setFaq((List<Map<String,String>>) (List<?>) l); }
        Object dyn = m.get("dynamic_attrs");
        if (dyn instanceof Map<?, ?> map) {
            Map<String, List<String>> conv = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                conv.put(String.valueOf(e.getKey()), (List<String>) e.getValue());
            }
            d.setDynamic_attrs(conv);
        }
    }
}


