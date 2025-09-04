package com.irondust.search.controller;

import com.irondust.search.config.VectorProperties;
import com.irondust.search.dto.SearchDtos;
import com.irondust.search.model.ProductDoc;
import com.irondust.search.service.FilterStringBuilder;
import com.irondust.search.service.HybridSearchService;
import com.irondust.search.service.MeiliService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
public class SearchController {
    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    private final MeiliService meiliService;
    private final HybridSearchService hybridSearchService;
    private final VectorProperties vectorProperties;

    public SearchController(MeiliService meiliService, HybridSearchService hybridSearchService, VectorProperties vectorProperties) {
        this.meiliService = meiliService;
        this.hybridSearchService = hybridSearchService;
        this.vectorProperties = vectorProperties;
    }

    /**
     * Adaptive search: runs fast lexical search by default and selectively triggers hybrid
     * (lexical + vector) when the query is semantic/cross-locale or when lexical recall is low.
     */
    @PostMapping("/search")
    public Mono<SearchDtos.SearchResponseBody<ProductDoc>> search(@Valid @RequestBody SearchDtos.SearchRequestBody body) {
        try {
            log.info("/search q='{}' page={} size={} lang={} filters_present={} sort_present={}",
                    body.getQ(), body.getPage(), body.getSize(), body.getLang(),
                    body.getFilters() != null, body.getSort() != null);
        } catch (Exception ignore) { /* best-effort logging */ }
        Map<String, Object> filters = body.getFilters();
        if (filters == null) {
            filters = new LinkedHashMap<>();
        }
        // Ensure in_stock default true unless explicitly set to false
        if (!filters.containsKey("in_stock")) {
            filters.put("in_stock", true);
        }
        String filter = FilterStringBuilder.build(filters);
        List<String> facets = List.of("brand_slug", "categories_slugs", "form", "diet_tags", "goal_tags");
        // If a single goal is selected, prefer sorting by its score desc
        List<String> computedSort = body.getSort();
        if (computedSort == null || computedSort.isEmpty()) {
            Object goalObj = filters.get("goal_tags");
            if (goalObj instanceof List<?> gl && gl.size() == 1) {
                String g = String.valueOf(gl.get(0));
                String scoreField = switch (g) {
                    case "preworkout" -> "goal_preworkout_score";
                    case "strength" -> "goal_strength_score";
                    case "endurance" -> "goal_endurance_score";
                    case "lean_muscle" -> "goal_lean_muscle_score";
                    case "recovery" -> "goal_recovery_score";
                    case "weight_loss" -> "goal_weight_loss_score";
                    case "wellness" -> "goal_wellness_score";
                    default -> null;
                };
                if (scoreField != null) {
                    computedSort = List.of(scoreField + ":desc");
                }
            }
        }
        final List<String> sort = computedSort;
        String q = body.getQ();
        if (shouldPreTriggerHybrid(q, body.getLang())) {
            return hybridSearchService.search(q, filter, sort, body.getPage(), body.getSize(), facets)
                    .map(raw -> mapRawToResponse(raw, body.getLang()));
        }

        return meiliService.searchRaw(q, filter, sort, body.getPage(), body.getSize(), facets)
                .flatMap(raw -> {
                    long total = extractTotal(raw);
                    boolean hasQuery = q != null && !q.isBlank();
                    int size = body.getSize() != null ? body.getSize() : 24;
                    boolean lowRecall = hasQuery && total < Math.max(24, size);
                    if (lowRecall && (q != null && q.trim().length() >= Math.max(1, vectorProperties.getMinQueryLength()))) {
                        return hybridSearchService.search(q, filter, sort, body.getPage(), body.getSize(), facets)
                                .map(hraw -> mapRawToResponse(hraw, body.getLang()));
                    }
                    return Mono.just(mapRawToResponse(raw, body.getLang()));
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
        // Optional AI dosage/timing fields
        d.setDosage_text((String) m.get("dosage_text"));
        d.setTiming_text((String) m.get("timing_text"));
        // phase 1 fields
        d.setForm((String) m.get("form"));
        Object fl = m.get("flavor");
        if (fl instanceof String s) {
            d.setFlavor(s);
        } else if (fl instanceof List<?> l && !l.isEmpty()) {
            Object first = l.get(0);
            if (first != null) {
                d.setFlavor(String.valueOf(first));
            }
        }
        d.setNet_weight_g((m.get("net_weight_g") instanceof Number)? ((Number) m.get("net_weight_g")).doubleValue() : null);
        d.setServings((m.get("servings") instanceof Number)? ((Number) m.get("servings")).intValue() : null);
        d.setServings_min((m.get("servings_min") instanceof Number)? ((Number) m.get("servings_min")).intValue() : null);
        d.setServings_max((m.get("servings_max") instanceof Number)? ((Number) m.get("servings_max")).intValue() : null);
        d.setServing_size_g((m.get("serving_size_g") instanceof Number)? ((Number) m.get("serving_size_g")).doubleValue() : null);
        // count-based packaging fields
        d.setUnit_count((m.get("unit_count") instanceof Number)? ((Number) m.get("unit_count")).intValue() : null);
        d.setUnits_per_serving((m.get("units_per_serving") instanceof Number)? ((Number) m.get("units_per_serving")).intValue() : null);
        d.setUnit_mass_g((m.get("unit_mass_g") instanceof Number)? ((Number) m.get("unit_mass_g")).doubleValue() : null);
        d.setPrice((m.get("price") instanceof Number)? ((Number) m.get("price")).doubleValue() : null);
        d.setPrice_per_serving((m.get("price_per_serving") instanceof Number)? ((Number) m.get("price_per_serving")).doubleValue() : null);
        d.setPrice_per_serving_min((m.get("price_per_serving_min") instanceof Number)? ((Number) m.get("price_per_serving_min")).doubleValue() : null);
        d.setPrice_per_serving_max((m.get("price_per_serving_max") instanceof Number)? ((Number) m.get("price_per_serving_max")).doubleValue() : null);
        d.setPrice_per_100g((m.get("price_per_100g") instanceof Number)? ((Number) m.get("price_per_100g")).doubleValue() : null);
        d.setPrice_per_unit((m.get("price_per_unit") instanceof Number)? ((Number) m.get("price_per_unit")).doubleValue() : null);
        d.setGoal_tags((List<String>) m.get("goal_tags"));
        d.setDiet_tags((List<String>) m.get("diet_tags"));
        d.setIngredients_key((List<String>) m.get("ingredients_key"));
        Object bs = m.get("benefit_snippet");
        if (bs instanceof String s) { d.setBenefit_snippet(s); }
        Object fq = m.get("faq");
        if (fq instanceof List<?> l) { d.setFaq((List<Map<String,String>>) (List<?>) l); }
        // Dynamic attributes can be returned either nested or flattened; reconstruct to support UI
        Map<String, List<String>> dynMap = new LinkedHashMap<>();
        Object dyn = m.get("dynamic_attrs");
        if (dyn instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) dyn).entrySet()) {
                dynMap.put(String.valueOf(e.getKey()), (List<String>) e.getValue());
            }
        } else {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (key == null) continue;
                if (key.startsWith("attr_") || key.equals("flavor") || key.equals("flavors") || key.equals("variant_group_id") || key.equals("warnings")) {
                    Object v = e.getValue();
                    if (v instanceof List<?> l) {
                        dynMap.put(key, (List<String>) (List<?>) l);
                    } else if (v != null) {
                        dynMap.put(key, List.of(String.valueOf(v)));
                    }
                }
            }
        }
        if (!dynMap.isEmpty()) d.setDynamic_attrs(dynMap);
        
        // Multilingual fields
        d.setName_i18n((Map<String, String>) m.get("name_i18n"));
        d.setDescription_i18n((Map<String, String>) m.get("description_i18n"));
        d.setShort_description_i18n((Map<String, String>) m.get("short_description_i18n"));
        d.setCategories_names_i18n((Map<String, List<String>>) m.get("categories_names_i18n"));
        d.setForm_i18n((Map<String, String>) m.get("form_i18n"));
        d.setFlavor_i18n((Map<String, String>) m.get("flavor_i18n"));
        d.setBenefit_snippet_i18n((Map<String, String>) m.get("benefit_snippet_i18n"));
        d.setDosage_text_i18n((Map<String, String>) m.get("dosage_text_i18n"));
        d.setTiming_text_i18n((Map<String, String>) m.get("timing_text_i18n"));
        d.setFaq_i18n((Map<String, List<Map<String, String>>>) m.get("faq_i18n"));
        d.setSearch_text_i18n((Map<String, String>) m.get("search_text_i18n"));
    }

    private void applyLanguageFields(ProductDoc d, String lang) {
        if (lang == null || lang.isEmpty()) return;
        
        // Apply language-specific name
        if (d.getName_i18n() != null && d.getName_i18n().containsKey(lang)) {
            d.setName(d.getName_i18n().get(lang));
        }
        
        // Apply language-specific categories
        if (d.getCategories_names_i18n() != null && d.getCategories_names_i18n().containsKey(lang)) {
            d.setCategories_names(d.getCategories_names_i18n().get(lang));
        }
        
        // Apply language-specific form
        if (d.getForm_i18n() != null && d.getForm_i18n().containsKey(lang)) {
            d.setForm(d.getForm_i18n().get(lang));
        }
        
        // Apply language-specific flavor
        if (d.getFlavor_i18n() != null && d.getFlavor_i18n().containsKey(lang)) {
            d.setFlavor(d.getFlavor_i18n().get(lang));
        }
        
        // Apply language-specific benefit snippet
        if (d.getBenefit_snippet_i18n() != null && d.getBenefit_snippet_i18n().containsKey(lang)) {
            d.setBenefit_snippet(d.getBenefit_snippet_i18n().get(lang));
        }
        // Apply language-specific dosage/timing
        if (d.getDosage_text_i18n() != null && d.getDosage_text_i18n().containsKey(lang)) {
            d.setDosage_text(d.getDosage_text_i18n().get(lang));
        }
        if (d.getTiming_text_i18n() != null && d.getTiming_text_i18n().containsKey(lang)) {
            d.setTiming_text(d.getTiming_text_i18n().get(lang));
        }
        
        // Apply language-specific FAQ
        if (d.getFaq_i18n() != null && d.getFaq_i18n().containsKey(lang)) {
            d.setFaq(d.getFaq_i18n().get(lang));
        }

        // Apply language-specific description
        if (d.getDescription_i18n() != null && d.getDescription_i18n().containsKey(lang)) {
            // Reuse 'search_text' for PDP primary description if present
            // but store translated description in search_text if search_text_i18n matches
            String desc = d.getDescription_i18n().get(lang);
            if (desc != null && !desc.isEmpty()) {
                d.setSearch_text(desc);
            }
        }

        // Apply language-specific search text override if present
        if (d.getSearch_text_i18n() != null && d.getSearch_text_i18n().containsKey(lang)) {
            String st = d.getSearch_text_i18n().get(lang);
            if (st != null && !st.isEmpty()) {
                d.setSearch_text(st);
            }
        }
    }

    /**
     * Maps raw Meilisearch (or fused hybrid) payload into API response and applies language overrides.
     */
    private SearchDtos.SearchResponseBody<ProductDoc> mapRawToResponse(Map<String, Object> raw, String lang) {
        SearchDtos.SearchResponseBody<ProductDoc> resp = new SearchDtos.SearchResponseBody<>();
        List<ProductDoc> items = new ArrayList<>();
        Object hitsObj = raw.get("hits");
        if (hitsObj instanceof List<?> hits) {
            for (Object h : hits) {
                if (h instanceof Map<?, ?> m) {
                    ProductDoc d = new ProductDoc();
                    mapToProductDoc(m, d);
                    applyLanguageFields(d, lang);
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
    }

    /**
     * Heuristics to decide whether to run hybrid first instead of purely lexical.
     */
    private boolean shouldPreTriggerHybrid(String q, String lang) {
        if (q == null || q.isBlank()) return false;
        String t = q.trim().toLowerCase(Locale.ROOT);
        int tokens = t.split("\\s+").length;
        if (tokens >= 4) return true;
        if (t.contains(" similar ") || t.contains(" like ") || t.contains("alternative") || t.contains("instead of")) return true;
        if (t.startsWith("similar ") || t.startsWith("like ") || t.startsWith("alternative ")) return true;
        if (looksCyrillic(t)) return true;
        if (hasEstonianMarkers(t)) return true;
        return false;
    }

    /** Quick Cyrillic check for cross-locale gating. */
    private boolean looksCyrillic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
            if (b == Character.UnicodeBlock.CYRILLIC || b == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY ||
                    b == Character.UnicodeBlock.CYRILLIC_EXTENDED_A || b == Character.UnicodeBlock.CYRILLIC_EXTENDED_B) {
                return true;
            }
        }
        return false;
    }

    /** Quick Estonian diacritics check for cross-locale gating. */
    private boolean hasEstonianMarkers(String text) {
        return text.indexOf('ä') >= 0 || text.indexOf('õ') >= 0 || text.indexOf('ö') >= 0 || text.indexOf('ü') >= 0;
    }

    /** Extracts total hits from Meili response supporting both fields. */
    private long extractTotal(Map<String, Object> raw) {
        Object totalObj = raw.containsKey("totalHits") ? raw.get("totalHits") : raw.getOrDefault("estimatedTotalHits", 0);
        return (totalObj instanceof Number) ? ((Number) totalObj).longValue() : 0L;
    }
}


