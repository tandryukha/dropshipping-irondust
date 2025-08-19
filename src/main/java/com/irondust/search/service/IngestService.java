package com.irondust.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.irondust.search.config.AppProperties;
import com.irondust.search.model.ProductDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final WooStoreService wooStoreService;
    private final MeiliService meiliService;
    private final AppProperties appProperties;

    public IngestService(WooStoreService wooStoreService, MeiliService meiliService, AppProperties appProperties) {
        this.wooStoreService = wooStoreService;
        this.meiliService = meiliService;
        this.appProperties = appProperties;
    }

    public Mono<Integer> ingestFull() {
        return wooStoreService.paginateProducts()
                .map(this::transform)
                .collectList()
                .flatMap(allDocs -> {
                    // discover dynamic facets
                    Set<String> dynamicFacetFields = new LinkedHashSet<>();
                    for (ProductDoc doc : allDocs) {
                        if (doc.getDynamic_attrs() != null) {
                            dynamicFacetFields.addAll(doc.getDynamic_attrs().keySet());
                        }
                    }
                    List<String> filterable = new ArrayList<>();
                    filterable.addAll(List.of("in_stock", "categories_slugs", "categories_ids", "brand_slug", "price_cents"));
                    filterable.addAll(dynamicFacetFields);
                    List<String> sortable = List.of("price_cents", "rating", "review_count", "in_stock");
                    List<String> searchable = List.of("name", "search_text", "sku");

                    // ensure index and settings first, then upload in chunks
                    return meiliService.ensureIndexWithSettings(filterable, sortable, searchable)
                            .thenMany(Flux.fromIterable(chunk(allDocs, 500)))
                            .concatMap(meiliService::addOrReplaceDocuments)
                            .then(Mono.just(allDocs.size()));
                });
    }

    public Mono<Integer> ingestByIds(List<Long> productIds) {
        return wooStoreService.fetchProductsByIds(productIds)
                .map(this::transform)
                .collectList()
                .flatMap(docs -> {
                    if (docs.isEmpty()) {
                        return Mono.just(0);
                    }
                    return meiliService.addOrReplaceDocuments(docs)
                            .then(Mono.just(docs.size()));
                });
    }

    private ProductDoc transform(JsonNode p) {
        ProductDoc d = new ProductDoc();
        long productId = p.path("id").asLong();
        d.setId("wc_" + productId);
        d.setParent_id(null);
        d.setType(p.path("type").asText(null));
        d.setSku(p.path("sku").asText(null));
        d.setSlug(p.path("slug").asText(null));
        d.setName(p.path("name").asText(null));
        d.setPermalink(p.path("permalink").asText(null));

        JsonNode prices = p.path("prices");
        if (!prices.isMissingNode()) {
            try {
                d.setPrice_cents(Integer.parseInt(prices.path("price").asText("0")));
            } catch (NumberFormatException e) { d.setPrice_cents(null); }
            d.setCurrency(prices.path("currency_code").asText(null));
        }

        d.setIn_stock(p.path("is_in_stock").asBoolean(false));
        d.setLow_stock_remaining(p.path("low_stock_remaining").isInt() ? p.path("low_stock_remaining").asInt() : null);
        d.setRating(p.path("average_rating").isNumber() ? p.path("average_rating").asDouble() : 0.0);
        d.setReview_count(p.path("review_count").isInt() ? p.path("review_count").asInt() : 0);

        List<String> images = new ArrayList<>();
        if (p.path("images").isArray()) {
            for (JsonNode img : p.path("images")) {
                String src = img.path("src").asText(null);
                if (src != null) images.add(src);
            }
        }
        d.setImages(images);

        List<Integer> catIds = new ArrayList<>();
        List<String> catSlugs = new ArrayList<>();
        List<String> catNames = new ArrayList<>();
        if (p.path("categories").isArray()) {
            for (JsonNode c : p.path("categories")) {
                if (c.path("id").isInt()) catIds.add(c.path("id").asInt());
                if (c.path("slug").isTextual()) catSlugs.add(c.path("slug").asText());
                if (c.path("name").isTextual()) catNames.add(c.path("name").asText());
            }
        }
        d.setCategories_ids(catIds);
        d.setCategories_slugs(catSlugs);
        d.setCategories_names(catNames);

        Map<String, List<String>> dynamic = new LinkedHashMap<>();
        String brandSlug = null;
        String brandName = null;
        if (p.path("attributes").isArray()) {
            for (JsonNode a : p.path("attributes")) {
                String taxonomy = a.path("taxonomy").asText("");
                if (taxonomy == null || taxonomy.isBlank()) continue;
                if (taxonomy.equals("pa_tootja")) {
                    if (a.path("terms").isArray() && a.path("terms").size() > 0) {
                        JsonNode t = a.path("terms").get(0);
                        brandSlug = t.path("slug").asText(null);
                        brandName = t.path("name").asText(null);
                    }
                }
                if (taxonomy.startsWith("pa_")) {
                    String key = "attr_" + taxonomy;
                    List<String> vals = new ArrayList<>();
                    if (a.path("terms").isArray()) {
                        for (JsonNode t : a.path("terms")) {
                            String slug = t.path("slug").asText(null);
                            if (slug != null) vals.add(slug);
                        }
                    }
                    dynamic.put(key, vals);
                }
            }
        }
        d.setBrand_slug(brandSlug);
        d.setBrand_name(brandName);
        d.setDynamic_attrs(dynamic);

        String description = p.path("description").asText("");
        String stripped = description.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        StringBuilder searchText = new StringBuilder();
        if (d.getName() != null) searchText.append(d.getName()).append(' ');
        searchText.append(stripped).append(' ');
        if (!catNames.isEmpty()) searchText.append(String.join(" ", catNames)).append(' ');
        if (brandName != null) searchText.append(brandName);
        d.setSearch_text(searchText.toString().trim());

        return d;
    }

    private static <T> List<List<T>> chunk(List<T> input, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < input.size(); i += size) {
            chunks.add(input.subList(i, Math.min(input.size(), i + size)));
        }
        return chunks;
    }
}


