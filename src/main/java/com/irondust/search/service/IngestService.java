package com.irondust.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.irondust.search.config.AppProperties;
import com.irondust.search.model.ProductDoc;
import com.irondust.search.model.RawProduct;
import com.irondust.search.model.EnrichedProduct;
import com.irondust.search.service.enrichment.EnrichmentPipeline;
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
    private final EnrichmentPipeline enrichmentPipeline;

    public IngestService(WooStoreService wooStoreService, MeiliService meiliService, 
                        AppProperties appProperties, EnrichmentPipeline enrichmentPipeline) {
        this.wooStoreService = wooStoreService;
        this.meiliService = meiliService;
        this.appProperties = appProperties;
        this.enrichmentPipeline = enrichmentPipeline;
    }

    public Mono<Integer> ingestFull() {
        return wooStoreService.paginateProducts()
                .map(this::transformWithEnrichment)
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
                    filterable.addAll(List.of("in_stock", "categories_slugs", "categories_ids", "brand_slug", "price_cents", 
                                             "form", "diet_tags", "goal_tags", "parent_id"));
                    filterable.addAll(dynamicFacetFields);
                    List<String> sortable = List.of("price_cents", "price", "price_per_serving", "price_per_100g", "rating", "review_count", "in_stock");
                    List<String> searchable = List.of("name", "search_text", "sku", "ingredients_key");

                    // ensure index and settings first, then upload in chunks
                    return meiliService.ensureIndexWithSettings(filterable, sortable, searchable)
                            .thenMany(Flux.fromIterable(chunk(allDocs, 500)))
                            .concatMap(meiliService::addOrReplaceDocuments)
                            .then(Mono.just(allDocs.size()));
                });
    }

    public Mono<Integer> ingestByIds(List<Long> productIds) {
        return wooStoreService.fetchProductsByIds(productIds)
                .map(this::transformWithEnrichment)
                .collectList()
                .flatMap(docs -> {
                    if (docs.isEmpty()) {
                        return Mono.just(0);
                    }
                    return meiliService.addOrReplaceDocuments(docs)
                            .then(Mono.just(docs.size()));
                });
    }

    private ProductDoc transformWithEnrichment(JsonNode p) {
        // Create raw product from JSON
        RawProduct raw = RawProduct.fromJsonNode(p);
        
        // Apply enrichment pipeline
        EnrichedProduct enriched = enrichmentPipeline.enrich(raw);
        
        // Convert enriched product to ProductDoc for Meilisearch
        ProductDoc d = new ProductDoc();
        d.setId(enriched.getId());
        d.setParent_id(enriched.getParent_id());
        d.setType(enriched.getType());
        d.setSku(enriched.getSku());
        d.setSlug(enriched.getSlug());
        d.setName(enriched.getName());
        d.setPermalink(enriched.getPermalink());
        d.setPrice_cents(enriched.getPrice_cents());
        d.setCurrency(enriched.getCurrency());
        d.setIn_stock(enriched.getIn_stock());
        d.setLow_stock_remaining(enriched.getLow_stock_remaining());
        d.setRating(enriched.getRating());
        d.setReview_count(enriched.getReview_count());
        d.setImages(enriched.getImages());
        d.setCategories_ids(enriched.getCategories_ids());
        d.setCategories_slugs(enriched.getCategories_slugs());
        d.setCategories_names(enriched.getCategories_names());
        d.setBrand_slug(enriched.getBrand_slug());
        d.setBrand_name(enriched.getBrand_name());
        d.setDynamic_attrs(enriched.getDynamic_attrs());
        d.setSearch_text(enriched.getSearch_text());

        // Add enriched fields to dynamic_attrs for backward compatibility
        Map<String, List<String>> enrichedAttrs = new LinkedHashMap<>(enriched.getDynamic_attrs() != null ? enriched.getDynamic_attrs() : new LinkedHashMap<>());
        
        if (enriched.getForm() != null) {
            enrichedAttrs.put("form", List.of(enriched.getForm()));
        }
        if (enriched.getFlavor() != null) {
            enrichedAttrs.put("flavor", List.of(enriched.getFlavor()));
        }
        if (enriched.getNet_weight_g() != null) {
            enrichedAttrs.put("net_weight_g", List.of(String.valueOf(enriched.getNet_weight_g())));
        }
        if (enriched.getServings() != null) {
            enrichedAttrs.put("servings", List.of(String.valueOf(enriched.getServings())));
        }
        if (enriched.getServing_size_g() != null) {
            enrichedAttrs.put("serving_size_g", List.of(String.valueOf(enriched.getServing_size_g())));
        }
        if (enriched.getPrice() != null) {
            enrichedAttrs.put("price", List.of(String.valueOf(enriched.getPrice())));
        }
        if (enriched.getPrice_per_serving() != null) {
            enrichedAttrs.put("price_per_serving", List.of(String.valueOf(enriched.getPrice_per_serving())));
        }
        if (enriched.getPrice_per_100g() != null) {
            enrichedAttrs.put("price_per_100g", List.of(String.valueOf(enriched.getPrice_per_100g())));
        }
        if (enriched.getGoal_tags() != null && !enriched.getGoal_tags().isEmpty()) {
            enrichedAttrs.put("goal_tags", enriched.getGoal_tags());
        }
        if (enriched.getDiet_tags() != null && !enriched.getDiet_tags().isEmpty()) {
            enrichedAttrs.put("diet_tags", enriched.getDiet_tags());
        }
        if (enriched.getIngredients_key() != null && !enriched.getIngredients_key().isEmpty()) {
            enrichedAttrs.put("ingredients_key", enriched.getIngredients_key());
        }
        if (enriched.getVariant_group_id() != null) {
            enrichedAttrs.put("variant_group_id", List.of(enriched.getVariant_group_id()));
        }
        if (enriched.getWarnings() != null && !enriched.getWarnings().isEmpty()) {
            enrichedAttrs.put("warnings", enriched.getWarnings());
        }

        d.setDynamic_attrs(enrichedAttrs);

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


