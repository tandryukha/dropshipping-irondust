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
                    List<String> searchable = List.of("name", "brand_name", "categories_names", "search_text", "sku", "ingredients_key", "synonyms_en", "synonyms_ru", "synonyms_et");

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
        
        // Ensure warnings state is clean per product
        enrichmentPipeline.clearWarnings();

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

        // Phase 1 parsed/enriched fields
        d.setForm(enriched.getForm());
        d.setFlavor(enriched.getFlavor());
        d.setNet_weight_g(enriched.getNet_weight_g());
        d.setServings(enriched.getServings());
        d.setServing_size_g(enriched.getServing_size_g());
        d.setPrice(enriched.getPrice());
        d.setPrice_per_serving(enriched.getPrice_per_serving());
        d.setPrice_per_100g(enriched.getPrice_per_100g());
        d.setGoal_tags(enriched.getGoal_tags());
        d.setDiet_tags(enriched.getDiet_tags());
        d.setIngredients_key(enriched.getIngredients_key());

        // Flatten AI synonyms if present (optional, Phase 1+)
        if (enriched.getSynonyms_multi() != null) {
            Map<String, java.util.List<String>> syn = enriched.getSynonyms_multi();
            java.util.List<String> en = syn.getOrDefault("en", java.util.List.of());
            java.util.List<String> ru = syn.getOrDefault("ru", java.util.List.of());
            java.util.List<String> et = syn.getOrDefault("et", java.util.List.of());
            d.setSynonyms_en(en);
            d.setSynonyms_ru(ru);
            d.setSynonyms_et(et);
        }

        // AI UX fields
        d.setBenefit_snippet(enriched.getBenefit_snippet());
        d.setFaq(enriched.getFaq());

        // Add enriched fields to dynamic_attrs for backward compatibility
        Map<String, List<String>> enrichedAttrs = new LinkedHashMap<>(enriched.getDynamic_attrs() != null ? enriched.getDynamic_attrs() : new LinkedHashMap<>());
        
        // Keep flavor for UI compatibility (chips), leave other derived fields as top-level
        if (enriched.getFlavor() != null) {
            enrichedAttrs.put("flavor", List.of(enriched.getFlavor()));
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


