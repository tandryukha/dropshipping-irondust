package com.irondust.search.controller;

import com.irondust.search.dto.SearchDtos;
import com.irondust.search.model.ProductDoc;

import java.util.*;

/** Utility mapper to reuse mapping logic between controllers. */
public class SearchControllerMapper {
    @SuppressWarnings("unchecked")
    public static SearchDtos.SearchResponseBody<ProductDoc> mapToResponse(Map<String, Object> raw, String lang) {
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
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) dmap).entrySet()) {
                        inner.put(String.valueOf(e.getKey()), ((Number) e.getValue()).intValue());
                    }
                    facetsDist.put(key, inner);
                }
            }
        }
        resp.setFacets(facetsDist);
        return resp;
    }

    private static void mapToProductDoc(Map<?, ?> m, ProductDoc d) {
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
        // Dynamic attributes: present either under dynamic_attrs or flattened
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

    private static void applyLanguageFields(ProductDoc d, String lang) {
        if (lang == null || lang.isEmpty()) return;
        if (d.getName_i18n() != null && d.getName_i18n().containsKey(lang)) d.setName(d.getName_i18n().get(lang));
        if (d.getCategories_names_i18n() != null && d.getCategories_names_i18n().containsKey(lang)) d.setCategories_names(d.getCategories_names_i18n().get(lang));
        if (d.getForm_i18n() != null && d.getForm_i18n().containsKey(lang)) d.setForm(d.getForm_i18n().get(lang));
        if (d.getFlavor_i18n() != null && d.getFlavor_i18n().containsKey(lang)) d.setFlavor(d.getFlavor_i18n().get(lang));
        if (d.getBenefit_snippet_i18n() != null && d.getBenefit_snippet_i18n().containsKey(lang)) d.setBenefit_snippet(d.getBenefit_snippet_i18n().get(lang));
        if (d.getDosage_text_i18n() != null && d.getDosage_text_i18n().containsKey(lang)) d.setDosage_text(d.getDosage_text_i18n().get(lang));
        if (d.getTiming_text_i18n() != null && d.getTiming_text_i18n().containsKey(lang)) d.setTiming_text(d.getTiming_text_i18n().get(lang));
        if (d.getFaq_i18n() != null && d.getFaq_i18n().containsKey(lang)) d.setFaq(d.getFaq_i18n().get(lang));
        if (d.getDescription_i18n() != null && d.getDescription_i18n().containsKey(lang)) d.setSearch_text(d.getDescription_i18n().get(lang));
        if (d.getSearch_text_i18n() != null && d.getSearch_text_i18n().containsKey(lang)) d.setSearch_text(d.getSearch_text_i18n().get(lang));
    }
}


