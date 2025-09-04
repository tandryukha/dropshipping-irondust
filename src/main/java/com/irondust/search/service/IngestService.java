package com.irondust.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.irondust.search.config.AppProperties;
import com.irondust.search.model.ProductDoc;
import com.irondust.search.model.RawProduct;
import com.irondust.search.model.EnrichedProduct;
import com.irondust.search.service.enrichment.EnrichmentPipeline;
import com.irondust.search.service.TranslationService.ProductTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.irondust.search.util.TokenAccounting;

import java.util.*;
import com.irondust.search.dto.IngestDtos;

@Service
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final WooStoreService wooStoreService;
    private final MeiliService meiliService;
    private final AppProperties appProperties;
    private final TranslationService translationService;
    private final FeatureFlagService featureFlags;
    private final BlacklistService blacklistService;

    public IngestService(WooStoreService wooStoreService, MeiliService meiliService, 
                        AppProperties appProperties, EnrichmentPipeline enrichmentPipeline,
                        TranslationService translationService, FeatureFlagService featureFlags,
                        BlacklistService blacklistService) {
        this.wooStoreService = wooStoreService;
        this.meiliService = meiliService;
        this.appProperties = appProperties;
        this.translationService = translationService;
        this.featureFlags = featureFlags;
        this.blacklistService = blacklistService;
    }

    public Mono<IngestDtos.IngestReport> ingestFull() {
        // Reset AI token accounting at the start of a full ingest run
        TokenAccounting.reset();
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
        int parallelism = Math.max(1, appProperties.getIngestParallelism());

        java.util.List<String> ignoredIds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        return wooStoreService.paginateProducts()
                .flatMap(json -> {
                        if (isBlacklisted(json)) {
                            String ignoredId = "wc_" + json.path("id").asLong();
                            log.info("Skipping blacklisted product {}", ignoredId);
                            ignoredIds.add(ignoredId);
                            return Mono.empty();
                        }
                        return transformWithEnrichmentWithReport(json)
                        .map(r -> {
                            int current = counter.incrementAndGet();
                            int warnCount = r.report.getWarnings() != null ? r.report.getWarnings().size() : 0;
                            int confCount = r.report.getConflicts() != null ? r.report.getConflicts().size() : 0;
                            log.info("Full ingest progress: {} items processed so far; id={} warnings={} conflicts={}",
                                    current, r.report.getId(), warnCount, confCount);
                            return r;
                        })
                        .subscribeOn(Schedulers.boundedElastic());
                    },
                        parallelism)
                .collectList()
                .flatMap(results -> {
                    List<ProductDoc> allDocs = new ArrayList<>();
                    List<IngestDtos.ProductReport> reports = new ArrayList<>();
                    for (var r : results) {
                        allDocs.add(r.doc);
                        reports.add(r.report);
                    }
                    // Merge same products (variants) by parent_id: collect flavors and min prices
                    applyVariantGroupingAggregates(allDocs);
                    // discover dynamic facets
                    Set<String> dynamicFacetFields = new LinkedHashSet<>();
                    for (ProductDoc doc : allDocs) {
                        if (doc.getDynamic_attrs() != null) {
                            dynamicFacetFields.addAll(doc.getDynamic_attrs().keySet());
                        }
                    }
                    List<String> filterable = new ArrayList<>();
                    filterable.addAll(List.of(
                            "in_stock", "categories_slugs", "categories_ids", "brand_slug", "price_cents",
                            "form", "diet_tags", "goal_tags", "parent_id", "is_on_sale",
                            // numeric price metrics
                            "price", "price_per_serving", "price_per_serving_min", "price_per_serving_max", "price_per_100g", "price_per_unit",
                            // count-based packaging
                            "unit_count", "units_per_serving"
                    ));
                    filterable.addAll(dynamicFacetFields);
                    List<String> sortable = new ArrayList<>(List.of(
                            "price_cents", "regular_price_cents", "sale_price_cents", "price", "price_per_serving", "price_per_serving_min", "price_per_serving_max",
                            "price_per_100g", "price_per_unit", "unit_count", "discount_pct",
                            "rating", "review_count", "in_stock",
                            // goal score sorts
                            "goal_preworkout_score", "goal_strength_score", "goal_endurance_score",
                            "goal_lean_muscle_score", "goal_recovery_score", "goal_weight_loss_score", "goal_wellness_score"
                    ));
                    List<String> searchable = List.of(
                            "name", "display_title", "brand_name", "categories_names", "search_text", "sku", "ingredients_key", 
                            "synonyms_en", "synonyms_ru", "synonyms_et",
                            // Multilingual fields
                            "name_i18n", "search_text_i18n", "categories_names_i18n"
                    );

                    // ensure index and settings first, then upload in chunks
                    int chunkSize = appProperties.getUploadChunkSize() > 0 ? appProperties.getUploadChunkSize() : 500;
                    int meiliConcurrency = Math.max(1, appProperties.getMeiliConcurrentUpdates());

                    java.util.Set<String> keepIds = allDocs.stream().map(ProductDoc::getId)
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

                    return meiliService.ensureIndexWithSettings(filterable, sortable, searchable)
                            .thenMany(Flux.fromIterable(chunk(allDocs, chunkSize)))
                            .flatMap(meiliService::addOrReplaceDocuments, meiliConcurrency)
                            .then(meiliService.pruneDocumentsNotIn(keepIds))
                            .then(Mono.fromSupplier(() -> buildReport(allDocs.size(), reports)))
                            .map(report -> {
                                report.setIgnored_ids(new java.util.ArrayList<>(ignoredIds));
                                report.setIgnored_count(ignoredIds.size());
                                attachAiUsage(report);
                                return report;
                            })
                            .flatMap(report -> persistFullIngestReport(report).thenReturn(report));
                });
    }

    public Mono<IngestDtos.IngestReport> ingestByIds(List<Long> productIds) {
        // Reset accounting per targeted ingest invocation
        TokenAccounting.reset();
        return wooStoreService.fetchProductsByIds(productIds)
                .index()
                .flatMap(tuple -> {
                    int current = (int) (tuple.getT1() + 1);
                    JsonNode json = tuple.getT2();
                    return transformWithEnrichmentWithReport(json)
                            .map(r -> {
                                int total = productIds.size();
                                int pct = (int) Math.round((current * 100.0) / Math.max(total, 1));
                                int warnCount = r.report.getWarnings() != null ? r.report.getWarnings().size() : 0;
                                int confCount = r.report.getConflicts() != null ? r.report.getConflicts().size() : 0;
                                log.info("Targeted ingest progress: {}/{} ({}%) id={} warnings={} conflicts={}",
                                        current, total, pct, r.report.getId(), warnCount, confCount);
                                return r;
                            });
                })
                .collectList()
                .flatMap(results -> {
                    if (results.isEmpty()) {
                        IngestDtos.IngestReport r = buildReport(0, List.of());
                        attachAiUsage(r);
                        return Mono.just(r);
                    }
                    List<ProductDoc> docs = new ArrayList<>();
                    List<IngestDtos.ProductReport> reports = new ArrayList<>();
                    for (var r : results) {
                        docs.add(r.doc);
                        reports.add(r.report);
                    }
                    // Apply grouping aggregates for partial ingests as well
                    applyVariantGroupingAggregates(docs);
                    return meiliService.addOrReplaceDocuments(docs)
                            .then(Mono.fromSupplier(() -> {
                                IngestDtos.IngestReport r = buildReport(docs.size(), reports);
                                attachAiUsage(r);
                                return r;
                            }));
                });
    }

    // Removed streaming SSE helpers; using only final JSON report endpoints

    private static class DocWithReport {
        final ProductDoc doc;
        final IngestDtos.ProductReport report;
        DocWithReport(ProductDoc doc, IngestDtos.ProductReport report) {
            this.doc = doc;
            this.report = report;
        }
    }

    private boolean isBlacklisted(JsonNode p) {
        try {
            String name = p.path("name").asText("").toLowerCase();
            String slug = p.path("slug").asText("").toLowerCase();
            String desc = p.path("description").asText("").toLowerCase();
            java.util.Set<String> tokens = new java.util.LinkedHashSet<>(java.util.List.of(
                "gift card", "gift-card", "giftcard", "present card", "voucher", "store credit",
                "kinkekaart", "kinke kaart", "kingitus", "presentkaart", "kingikaart", "kinkekaardid"
            ));
            for (String t : tokens) {
                if (name.contains(t) || slug.contains(t) || desc.contains(t)) {
                    return true;
                }
            }
            if (p.path("categories").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode c : p.path("categories")) {
                    String cs = c.path("slug").asText("").toLowerCase();
                    String cn = c.path("name").asText("").toLowerCase();
                    // Direct category slug/name checks
                    if (cs.contains("gift") || cs.contains("voucher") || cs.contains("kinkekaard")) return true;
                    if (cn.contains("gift") || cn.contains("voucher") || cn.contains("kinkekaard")) return true;
                    for (String t : tokens) {
                        if (cs.contains(t.replace(" ", "-")) || cn.contains(t)) return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private Mono<DocWithReport> transformWithEnrichmentWithReport(JsonNode p) {
        // Static blacklist (content/category heuristics)
        if (isBlacklisted(p)) {
            String skippedId = "wc_" + p.path("id").asLong();
            log.info("Skipping blacklisted product {} (static rules)", skippedId);
            return Mono.empty();
        }

        // Dynamic blacklist (DB-backed): skip if present
        String candidateId = "wc_" + p.path("id").asLong();
        return blacklistService.isBlacklistedId(candidateId)
                .flatMap(isBl -> {
                    if (isBl) {
                        log.info("Skipping blacklisted product {} (dynamic)", candidateId);
                        return Mono.empty();
                    }

                    // Create raw product from JSON
                    RawProduct raw = RawProduct.fromJsonNode(p);

                    // Use a fresh pipeline instance per product to ensure thread-safety under parallelism
                    // TitleComposer controlled via feature flag 'normalize_titles'.
                    return featureFlags.isEnabled("normalize_titles", false)
                            .flatMap(enableTitle -> {
                                EnrichmentPipeline pipeline = new EnrichmentPipeline(enableTitle);
                                EnrichedProduct enriched = pipeline.enrich(raw);

                                // Always attempt translations - TranslationService will handle enabling/disabling based on API key
                                return translateProduct(enriched)
                                        .map(translations -> {
                                            // Merge translation warnings into product warnings for reporting
                                            java.util.List<String> mergedWarnings = new java.util.ArrayList<>(
                                                    enriched.getWarnings() != null ? enriched.getWarnings() : java.util.List.of());
                                            if (translations != null) {
                                                for (java.util.Map.Entry<String, ProductTranslation> e : translations.entrySet()) {
                                                    String lang = e.getKey();
                                                    ProductTranslation tr = e.getValue();
                                                    if (tr != null && tr.warnings != null && !tr.warnings.isEmpty()) {
                                                        for (String w : tr.warnings) {
                                                            mergedWarnings.add("translation_" + lang + ": " + w);
                                                        }
                                                    }
                                                }
                                            }
                                            if (!mergedWarnings.isEmpty()) {
                                                enriched.setWarnings(mergedWarnings);
                                            }

                                            ProductDoc d = createProductDoc(enriched, translations);
                                            IngestDtos.ProductReport report = createReport(enriched);
                                            return new DocWithReport(d, report);
                                        })
                                        .onErrorResume(e -> {
                                            log.error("Translation failed for product {}: {}", enriched.getId(), e.getMessage());
                                            // Fall back to non-translated version
                                            ProductDoc d = createProductDoc(enriched, null);
                                            IngestDtos.ProductReport report = createReport(enriched);
                                            return Mono.just(new DocWithReport(d, report));
                                        });
                            });
                });
    }
    
    private ProductDoc createProductDoc(EnrichedProduct enriched, Map<String, ProductTranslation> translations) {
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
        d.setRegular_price_cents(enriched.getRegular_price_cents());
        d.setSale_price_cents(enriched.getSale_price_cents());
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
        // Optional display title for UI (feature-flagged generation)
        d.setDisplay_title(enriched.getDisplay_title());
        
        // Add translations if available
        if (translations != null && !translations.isEmpty()) {
            Map<String, String> nameI18n = new HashMap<>();
            Map<String, String> descI18n = new HashMap<>();
            Map<String, String> shortDescI18n = new HashMap<>();
            Map<String, List<String>> categoriesI18n = new HashMap<>();
            Map<String, String> formI18n = new HashMap<>();
            Map<String, String> flavorI18n = new HashMap<>();
            Map<String, String> benefitSnippetI18n = new HashMap<>();
            Map<String, String> dosageI18n = new HashMap<>();
            Map<String, String> timingI18n = new HashMap<>();
            Map<String, String> searchTextI18n = new HashMap<>();
            Map<String, List<Map<String, String>>> faqI18n = new HashMap<>();
            
            for (Map.Entry<String, ProductTranslation> entry : translations.entrySet()) {
                String lang = entry.getKey();
                ProductTranslation trans = entry.getValue();
                
                if (trans.name != null) nameI18n.put(lang, trans.name);
                if (trans.description != null) descI18n.put(lang, trans.description);
                if (trans.shortDescription != null) shortDescI18n.put(lang, trans.shortDescription);
                if (trans.benefitSnippet != null) benefitSnippetI18n.put(lang, trans.benefitSnippet);
                if (trans.categories != null && !trans.categories.isEmpty()) {
                    categoriesI18n.put(lang, trans.categories);
                }
                if (trans.form != null) formI18n.put(lang, trans.form);
                if (trans.flavor != null) flavorI18n.put(lang, trans.flavor);
                if (enriched.getDosage_text() != null) {
                    String v = (trans.dosageText != null && !trans.dosageText.isBlank()) ? trans.dosageText : enriched.getDosage_text();
                    dosageI18n.put(lang, v);
                }
                if (enriched.getTiming_text() != null) {
                    String v = (trans.timingText != null && !trans.timingText.isBlank()) ? trans.timingText : enriched.getTiming_text();
                    timingI18n.put(lang, v);
                }
                
                // Convert FAQ format
                if (trans.faq != null && !trans.faq.isEmpty()) {
                    List<Map<String, String>> faqList = new ArrayList<>();
                    for (ProductTranslation.FaqItem item : trans.faq) {
                        Map<String, String> faqMap = new HashMap<>();
                        faqMap.put("q", item.q);
                        faqMap.put("a", item.a);
                        faqList.add(faqMap);
                    }
                    faqI18n.put(lang, faqList);
                }
                
                // Build search text for each language
                String searchText = buildSearchText(trans.name, trans.description, 
                    trans.categories, enriched.getBrand_name());
                if (searchText != null && !searchText.isEmpty()) {
                    searchTextI18n.put(lang, searchText);
                }
            }
            
            d.setName_i18n(nameI18n);
            d.setDescription_i18n(descI18n);
            d.setShort_description_i18n(shortDescI18n);
            d.setCategories_names_i18n(categoriesI18n);
            d.setForm_i18n(formI18n);
            d.setFlavor_i18n(flavorI18n);
            d.setBenefit_snippet_i18n(benefitSnippetI18n);
            d.setDosage_text_i18n(dosageI18n);
            d.setTiming_text_i18n(timingI18n);
            d.setFaq_i18n(faqI18n);
            d.setSearch_text_i18n(searchTextI18n);
        }

        // Phase 1 parsed/enriched fields
        d.setForm(enriched.getForm());
        d.setFlavor(enriched.getFlavor());
        d.setNet_weight_g(enriched.getNet_weight_g());
        d.setServings(enriched.getServings());
        d.setServings_min(enriched.getServings_min());
        d.setServings_max(enriched.getServings_max());
        d.setServing_size_g(enriched.getServing_size_g());
        d.setUnit_count(enriched.getUnit_count());
        d.setUnits_per_serving(enriched.getUnits_per_serving());
        d.setUnit_mass_g(enriched.getUnit_mass_g());
        d.setPrice_per_unit(enriched.getPrice_per_unit());
        d.setPrice(enriched.getPrice());
        d.setPrice_per_serving(enriched.getPrice_per_serving());
        d.setPrice_per_serving_min(enriched.getPrice_per_serving_min());
        d.setPrice_per_serving_max(enriched.getPrice_per_serving_max());
        d.setPrice_per_100g(enriched.getPrice_per_100g());
        d.setDiscount_pct(enriched.getDiscount_pct());
        d.setIs_on_sale(enriched.getIs_on_sale());
        d.setDiscount_pct(enriched.getDiscount_pct());
        d.setIs_on_sale(enriched.getIs_on_sale());
        d.setGoal_tags(enriched.getGoal_tags());
        d.setDiet_tags(enriched.getDiet_tags());
        d.setIngredients_key(enriched.getIngredients_key());
        // goal scores
        d.setGoal_preworkout_score(enriched.getGoal_preworkout_score());
        d.setGoal_strength_score(enriched.getGoal_strength_score());
        d.setGoal_endurance_score(enriched.getGoal_endurance_score());
        d.setGoal_lean_muscle_score(enriched.getGoal_lean_muscle_score());
        d.setGoal_recovery_score(enriched.getGoal_recovery_score());
        d.setGoal_weight_loss_score(enriched.getGoal_weight_loss_score());
        d.setGoal_wellness_score(enriched.getGoal_wellness_score());

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
        d.setDosage_text(enriched.getDosage_text());
        d.setTiming_text(enriched.getTiming_text());

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
    
    private IngestDtos.ProductReport createReport(EnrichedProduct enriched) {
        IngestDtos.ProductReport rep = new IngestDtos.ProductReport();
        rep.setId(enriched.getId());
        // Standardize to empty arrays instead of nulls for easier client handling
        rep.setWarnings(enriched.getWarnings() != null ? enriched.getWarnings() : java.util.List.of());
        rep.setConflicts(enriched.getConflicts() != null ? enriched.getConflicts() : java.util.List.of());
        return rep;
    }

    private String buildSearchText(String name, String description, List<String> categories, String brand) {
        List<String> parts = new ArrayList<>();
        if (name != null && !name.isEmpty()) parts.add(name);
        if (description != null && !description.isEmpty()) {
            // Strip HTML tags from description
            String cleanDesc = description.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            if (!cleanDesc.isEmpty()) parts.add(cleanDesc);
        }
        if (categories != null) {
            parts.addAll(categories);
        }
        if (brand != null && !brand.isEmpty()) parts.add(brand);
        return String.join(" ", parts);
    }
    
    private Mono<Map<String, ProductTranslation>> translateProduct(EnrichedProduct enriched) {
        // Prepare source data for translation
        ProductTranslation source = new ProductTranslation();
        source.name = enriched.getName();
        source.description = enriched.getDescription();
        source.shortDescription = null; // Not available in current data model
        source.benefitSnippet = enriched.getBenefit_snippet();
        source.categories = enriched.getCategories_names();
        source.form = enriched.getForm();
        source.flavor = enriched.getFlavor();
        
        // Convert FAQ format
        if (enriched.getFaq() != null && !enriched.getFaq().isEmpty()) {
            source.faq = new ArrayList<>();
            for (Map<String, String> faqItem : enriched.getFaq()) {
                String q = faqItem.get("q");
                String a = faqItem.get("a");
                if (q != null && a != null) {
                    source.faq.add(new ProductTranslation.FaqItem(q, a));
                }
            }
        }
        
        // Let the translation service detect the source language
        String sourceLanguage = null; // Will be auto-detected
        
        return translationService.translateProduct(sourceLanguage, source);
    }
    
    private static <T> List<List<T>> chunk(List<T> input, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < input.size(); i += size) {
            chunks.add(input.subList(i, Math.min(input.size(), i + size)));
        }
        return chunks;
    }

    private IngestDtos.IngestReport buildReport(int indexed, List<IngestDtos.ProductReport> products) {
        IngestDtos.IngestReport report = new IngestDtos.IngestReport();
        report.setIndexed(indexed);
        report.setProducts(products);
        int warningsTotal = 0;
        int conflictsTotal = 0;
        for (IngestDtos.ProductReport pr : products) {
            if (pr.getWarnings() != null) warningsTotal += pr.getWarnings().size();
            if (pr.getConflicts() != null) conflictsTotal += pr.getConflicts().size();
        }
        report.setWarnings_total(warningsTotal);
        report.setConflicts_total(conflictsTotal);
        return report;
    }

    private void attachAiUsage(IngestDtos.IngestReport report) {
        try {
            java.util.Map<String, com.irondust.search.util.TokenAccounting.UsageWithCost> snap = com.irondust.search.util.TokenAccounting.snapshotWithCosts();
            java.util.Map<String, java.util.Map<String, Object>> out = new java.util.LinkedHashMap<>();
            for (var e : snap.entrySet()) {
                var u = e.getValue();
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("prompt_tokens", u.promptTokens);
                m.put("completion_tokens", u.completionTokens);
                m.put("total_tokens", u.totalTokens);
                m.put("cost_usd", u.costUsd);
                out.put(e.getKey(), m);
            }
            report.setAi_usage_per_model(out);
            report.setAi_cost_total_usd(com.irondust.search.util.TokenAccounting.totalCostUsd(snap));
        } catch (Exception ignored) {}
    }

    /**
     * Aggregates variant groups (same parent_id) to enable UI grouping by flavor and to show
     * the cheapest price for the group. This method mutates the provided list of documents
     * in-place by:
     * - Adding dynamic_attrs.flavors: union of flavors across the group (deduplicated)
     * - Overriding price/price_cents with the group's minimum
     * - Overriding price_per_serving and price_per_100g with the group's minimum when available
     * - Propagating sale metadata (is_on_sale, discount_pct) from the cheapest variant
     */
    private void applyVariantGroupingAggregates(List<ProductDoc> docs) {
        if (docs == null || docs.isEmpty()) return;

        class GroupAgg {
            java.util.Set<String> flavors = new java.util.LinkedHashSet<>();
            Integer minPriceCents = null;
            Double minPrice = null;
            Double minPps = null;
            Double minP100 = null;
            Boolean isOnSale = null;
            Double discountPct = null;
        }

        java.util.Map<String, GroupAgg> byParent = new java.util.LinkedHashMap<>();

        for (ProductDoc d : docs) {
            String parent = d != null ? d.getParent_id() : null;
            // Still collect flavors even when parent is missing to expose a single-option list
            String fl = extractFlavorValue(d);
            if (parent == null || parent.isBlank()) {
                ensureFlavorsArray(d, fl != null ? java.util.List.of(fl) : java.util.List.of());
                continue;
            }
            GroupAgg g = byParent.computeIfAbsent(parent, k -> new GroupAgg());
            if (fl != null && !fl.isBlank()) g.flavors.add(fl.trim());

            if (d.getPrice_cents() != null) {
                if (g.minPriceCents == null || d.getPrice_cents() < g.minPriceCents) {
                    g.minPriceCents = d.getPrice_cents();
                    g.minPrice = (d.getPrice() != null) ? d.getPrice() : (d.getPrice_cents() / 100.0);
                    g.isOnSale = d.getIs_on_sale();
                    g.discountPct = d.getDiscount_pct();
                }
            }
            if (d.getPrice_per_serving() != null) {
                g.minPps = (g.minPps == null) ? d.getPrice_per_serving() : Math.min(g.minPps, d.getPrice_per_serving());
            }
            if (d.getPrice_per_100g() != null) {
                g.minP100 = (g.minP100 == null) ? d.getPrice_per_100g() : Math.min(g.minP100, d.getPrice_per_100g());
            }
        }

        for (ProductDoc d : docs) {
            String parent = d != null ? d.getParent_id() : null;
            if (parent == null || parent.isBlank()) continue;
            GroupAgg g = byParent.get(parent);
            if (g == null) continue;

            // Apply flavors list
            ensureFlavorsArray(d, new java.util.ArrayList<>(g.flavors));

            // Apply group min pricing
            if (g.minPriceCents != null) d.setPrice_cents(g.minPriceCents);
            if (g.minPrice != null) d.setPrice(g.minPrice);
            if (g.minPps != null) d.setPrice_per_serving(g.minPps);
            if (g.minP100 != null) d.setPrice_per_100g(g.minP100);
            if (g.isOnSale != null) d.setIs_on_sale(g.isOnSale);
            if (g.discountPct != null) d.setDiscount_pct(g.discountPct);
        }
    }

    private static void ensureFlavorsArray(ProductDoc d, java.util.List<String> flavors) {
        if (d == null) return;
        java.util.Map<String, java.util.List<String>> dyn = d.getDynamic_attrs();
        if (dyn == null) {
            dyn = new java.util.LinkedHashMap<>();
            d.setDynamic_attrs(dyn);
        }
        if (flavors == null) flavors = java.util.List.of();
        // Deduplicate and keep stable order
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String s : flavors) { if (s != null && !s.isBlank()) set.add(s.trim()); }
        dyn.put("flavors", new java.util.ArrayList<>(set));
    }

    private static String extractFlavorValue(ProductDoc d) {
        if (d == null) return null;
        if (d.getFlavor() != null && !d.getFlavor().isBlank()) return d.getFlavor();
        java.util.Map<String, java.util.List<String>> dyn = d.getDynamic_attrs();
        if (dyn != null) {
            java.util.List<String> v = dyn.get("flavor");
            if (v == null || v.isEmpty()) v = dyn.get("attr_pa_maitse");
            if (v != null && !v.isEmpty() && v.get(0) != null && !v.get(0).isBlank()) return v.get(0).trim();
        }
        // Fallback: guess from product name suffix or parenthesized part
        try {
            String name = d.getName();
            if (name != null) {
                String lowered = name.toLowerCase();
                // Prefer bracketed parts like "(vanill)"
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\(\u005B\u3010]([^\\)\u005D\u3011]{2,32})[\\)\u005D\u3011]\s*$").matcher(lowered);
                if (m.find()) {
                    String in = m.group(1).trim();
                    if (!in.isBlank()) return capitalizeFlavor(in);
                }
                // Known multi-word patterns first
                String[] multi = {"valge šokolaad","valge sokolaad","kookos-šokolaad","kookos sokolaad","white chocolate"};
                for (String p : multi) { if (lowered.contains(p)) return capitalizeFlavor(p); }
                // Single-word/short tokens
                String[] tokens = {"vanill","vanilla","šokolaad","sokolaad","kookos","maasikas","vaarikas","metsamarja","banaan","kirss","apelsin","sidrun","laim","mustikas","tropical","troopiline","kola","cola","citrus","caramel","karamell","kohv","coffee"};
                for (String t : tokens) { if (lowered.matches(".*\\b"+java.util.regex.Pattern.quote(t)+"\\b.*")) return capitalizeFlavor(t); }
                // As a last resort, take the last word if it looks like a flavor word (letters only)
                String[] parts = lowered.replaceAll("[0-9]+\\s*(g|kg|ml|l|servings?|portsjonid?)"," ").trim().split("\\s+");
                if (parts.length >= 1) {
                    String last = parts[parts.length - 1];
                    if (last.matches("[a-z\u00C0-\u024F\u0100-\u017F-]{3,}")) return capitalizeFlavor(last);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String capitalizeFlavor(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;
        // Simple title-case for first letter; keep diacritics
        return Character.toUpperCase(trimmed.charAt(0)) + (trimmed.length() > 1 ? trimmed.substring(1) : "");
    }

    /**
     * Persist a JSON snapshot of the final full-ingest report for historical auditing.
     * Files are written to app.ingestHistoryDir (default: tmp/ingest-history) using
     * a timestamped filename like ingest_YYYYMMDD_HHmmss.json.
     */
    private Mono<Void> persistFullIngestReport(IngestDtos.IngestReport report) {
        return Mono.fromRunnable(() -> {
            try {
                String dir = appProperties.getIngestHistoryDir();
                if (dir == null || dir.isBlank()) {
                    dir = "tmp/ingest-history";
                }
                java.nio.file.Path historyDir = java.nio.file.Paths.get(dir);
                java.nio.file.Files.createDirectories(historyDir);

                java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
                String ts = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssXXX"));
                String fileName = "ingest_" + ts.replace(":", "-") + ".json";
                java.nio.file.Path out = historyDir.resolve(fileName);

                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                om.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), report);
                log.info("Saved full-ingest report to {}", out.toAbsolutePath());
            } catch (Exception e) {
                log.warn("Failed to persist full-ingest report: {}", e.toString());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}


