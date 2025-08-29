package com.irondust.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.irondust.search.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translation service for product localization.
 * Supports Estonian (est), English (en), and Russian (ru).
 * Uses OpenAI API for translations with intelligent caching and batch processing.
 */
@Service
public class TranslationService {
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final Map<String, TranslationCache> translationCache = new ConcurrentHashMap<>();
    private final String apiKey;
    private final String model;
    private final boolean enabled;
    
    // Supported languages
    public static final String LANG_EST = "est";
    public static final String LANG_EN = "en";
    public static final String LANG_RU = "ru";
    public static final List<String> SUPPORTED_LANGUAGES = List.of(LANG_EST, LANG_EN, LANG_RU);
    
    // Cache entry with expiration
    private static class TranslationCache {
        final Map<String, String> translations;
        final long timestamp;
        
        TranslationCache(Map<String, String> translations) {
            this.translations = translations;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            // Cache for 24 hours
            return System.currentTimeMillis() - timestamp > 86400000;
        }
    }
    
    public TranslationService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        
        // Use the same environment variables as AIEnricher
        this.apiKey = System.getenv("OPENAI_API_KEY");
        String modelEnv = System.getenv("OPENAI_MODEL");
        this.model = (modelEnv == null || modelEnv.isBlank()) ? "gpt-4o-mini" : modelEnv;
        
        // Enable translations when API key is present (same as AI enrichment)
        this.enabled = apiKey != null && !apiKey.isBlank();
        
        if (!enabled) {
            log.info("Translations disabled: OPENAI_API_KEY not found");
        } else {
            log.info("Translations enabled with model: {}", model);
        }
        
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + (apiKey != null ? apiKey : ""))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
    
    /**
     * Translates product fields from source language to all target languages.
     * Returns a map of language code to translated fields.
     */
    public Mono<Map<String, ProductTranslation>> translateProduct(
            String sourceLanguage, 
            ProductTranslation sourceData) {
        
        // If translations are disabled, return only the original
        if (!enabled) {
            Map<String, ProductTranslation> result = new HashMap<>();
            result.put(sourceLanguage != null ? sourceLanguage : LANG_EST, sourceData);
            return Mono.just(result);
        }
        
        // If source language is not detected, try to detect it
        final String finalSourceLanguage;
        if (sourceLanguage == null || sourceLanguage.isEmpty()) {
            finalSourceLanguage = detectLanguage(sourceData);
        } else {
            finalSourceLanguage = sourceLanguage;
        }
        
        // Build list of target languages (all except source)
        List<String> targetLanguages = SUPPORTED_LANGUAGES.stream()
                .filter(lang -> !lang.equals(finalSourceLanguage))
                .toList();
        
        // Translate to each target language
        Map<String, ProductTranslation> result = new HashMap<>();
        result.put(finalSourceLanguage, sourceData); // Include original
        
        return Mono.just(targetLanguages)
                .flatMapIterable(list -> list)
                .flatMap(targetLang -> 
                    translateToLanguage(finalSourceLanguage, targetLang, sourceData)
                        .map(translation -> Map.entry(targetLang, translation))
                )
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(translations -> {
                    result.putAll(translations);
                    return result;
                })
                .doOnError(e -> log.error("Translation failed: {}", e.getMessage()))
                .onErrorReturn(result); // Return at least original on error
    }
    
    /**
     * Translates text from source to target language using OpenAI API.
     * Handles product-specific terminology and maintains consistency.
     */
    private Mono<ProductTranslation> translateToLanguage(
            String sourceLang, 
            String targetLang, 
            ProductTranslation source) {
        
        // Check cache first
        String cacheKey = buildCacheKey(sourceLang, targetLang, source);
        TranslationCache cached = translationCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return Mono.just(mapToProductTranslation(cached.translations));
        }
        
        // Build translation request
        String systemPrompt = buildSystemPrompt(sourceLang, targetLang);
        String userContent = buildTranslationContent(source);
        
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("temperature", 0.1); // Low temperature for consistency
        request.put("max_tokens", 2000);
        
        ArrayNode messages = request.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        
        // Add response format for structured output
        ObjectNode responseFormat = request.putObject("response_format");
        responseFormat.put("type", "json_object");
        
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request.toString())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .map(response -> parseTranslationResponse(response, source))
                .doOnSuccess(translation -> {
                    // Cache the result
                    Map<String, String> translationMap = productTranslationToMap(translation);
                    translationCache.put(cacheKey, new TranslationCache(translationMap));
                })
                .doOnError(e -> log.error("OpenAI translation error: {}", e.getMessage()))
                .onErrorReturn(source); // Return original on error
    }
    
    private String buildSystemPrompt(String sourceLang, String targetLang) {
        String sourceLanguageName = getLanguageName(sourceLang);
        String targetLanguageName = getLanguageName(targetLang);
        
        return String.format("""
            You are a professional translator specializing in e-commerce and sports nutrition products.
            Translate the following product information from %s to %s.
            
            Guidelines:
            1. Maintain accuracy for technical terms (proteins, vitamins, supplements)
            2. Keep brand names unchanged
            3. Translate product names naturally while preserving key terms
            4. Ensure descriptions are fluent and natural in the target language
            5. Preserve HTML tags if present
            6. Keep measurement units (g, kg, ml, etc.) unchanged
            7. For product forms (powder, capsules, etc.), use standard translations
            8. Maintain consistent terminology across all fields
            
            Return a JSON object with these exact fields:
            {
                "name": "translated product name",
                "description": "translated description or null",
                "short_description": "translated short description or null",
                "benefit_snippet": "translated benefit snippet or null",
                "categories": ["translated", "category", "names"],
                "form": "translated form or null",
                "flavor": "translated flavor or null",
                "faq": [
                    {"q": "translated question", "a": "translated answer"},
                    ...
                ]
            }
            
            If a field is null or empty in the source, keep it null in the translation.
            """, sourceLanguageName, targetLanguageName);
    }
    
    private String buildTranslationContent(ProductTranslation source) {
        Map<String, Object> content = new HashMap<>();
        content.put("name", source.name);
        content.put("description", source.description);
        content.put("short_description", source.shortDescription);
        content.put("benefit_snippet", source.benefitSnippet);
        content.put("categories", source.categories);
        content.put("form", source.form);
        content.put("flavor", source.flavor);
        
        // Convert FAQ to simple format
        if (source.faq != null && !source.faq.isEmpty()) {
            List<Map<String, String>> faqList = new ArrayList<>();
            for (ProductTranslation.FaqItem item : source.faq) {
                Map<String, String> faqMap = new HashMap<>();
                faqMap.put("q", item.q);
                faqMap.put("a", item.a);
                faqList.add(faqMap);
            }
            content.put("faq", faqList);
        } else {
            content.put("faq", new ArrayList<>());
        }
        
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            log.error("Failed to serialize translation content", e);
            return "{}";
        }
    }
    
    private ProductTranslation parseTranslationResponse(JsonNode response, ProductTranslation fallback) {
        try {
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !content.isTextual()) {
                return fallback;
            }
            
            JsonNode translationJson = objectMapper.readTree(content.asText());
            
            ProductTranslation result = new ProductTranslation();
            result.name = translationJson.path("name").asText(fallback.name);
            result.description = translationJson.path("description").isNull() ? null : 
                    translationJson.path("description").asText(null);
            result.shortDescription = translationJson.path("short_description").isNull() ? null :
                    translationJson.path("short_description").asText(null);
            
            // Parse categories array
            if (translationJson.has("categories") && translationJson.get("categories").isArray()) {
                result.categories = new ArrayList<>();
                translationJson.get("categories").forEach(cat -> result.categories.add(cat.asText()));
            } else {
                result.categories = fallback.categories;
            }
            
            result.form = translationJson.path("form").isNull() ? null :
                    translationJson.path("form").asText(null);
            result.flavor = translationJson.path("flavor").isNull() ? null :
                    translationJson.path("flavor").asText(null);
            result.benefitSnippet = translationJson.path("benefit_snippet").isNull() ? null :
                    translationJson.path("benefit_snippet").asText(null);
            
            // Parse FAQ array
            if (translationJson.has("faq") && translationJson.get("faq").isArray()) {
                result.faq = new ArrayList<>();
                translationJson.get("faq").forEach(faqNode -> {
                    String q = faqNode.path("q").asText(null);
                    String a = faqNode.path("a").asText(null);
                    if (q != null && a != null) {
                        result.faq.add(new ProductTranslation.FaqItem(q, a));
                    }
                });
            } else {
                result.faq = fallback.faq;
            }
            
            return result;
        } catch (Exception e) {
            log.error("Failed to parse translation response", e);
            return fallback;
        }
    }
    
    /**
     * Attempts to detect the source language of the product data.
     * Defaults to Estonian if detection fails.
     */
    private String detectLanguage(ProductTranslation data) {
        // Build text for detection (prefer description, then name; benefit snippet last)
        StringBuilder textBuilder = new StringBuilder();
        if (data.description != null) textBuilder.append(data.description).append(" ");
        if (data.name != null) textBuilder.append(data.name).append(" ");
        if (data.benefitSnippet != null) textBuilder.append(data.benefitSnippet).append(" ");

        String textRaw = textBuilder.toString();
        String text = textRaw.toLowerCase();

        // 1) Cyrillic quick check → Russian
        if (textRaw.matches(".*\\p{InCyrillic}.*")) {
            return LANG_RU;
        }

        // 2) Estonian markers: diacritics and common headings/words seen in product descriptions
        if (text.contains("toote nimetus") || text.contains("vorm:") || text.contains("koostis") ||
            text.contains("koostisosad") || text.contains("hoiatus") || text.contains("soovitatav") ||
            text.contains("päevane annus") || text.contains("tootja:") ||
            text.indexOf('ä') >= 0 || text.indexOf('õ') >= 0 || text.indexOf('ö') >= 0 || text.indexOf('ü') >= 0) {
            return LANG_EST;
        }

        // 3) English markers
        if (text.contains(" the ") || text.contains(" for ") || text.contains(" and ") ||
            text.contains(" with ") || text.contains(" from ") || text.contains("protein") ||
            text.contains("supplement") || text.contains("daily") || text.contains("servings")) {
            return LANG_EN;
        }

        // Fallback: Estonian (primary source)
        return LANG_EST;
    }
    
    private String getLanguageName(String code) {
        return switch (code) {
            case LANG_EST -> "Estonian";
            case LANG_EN -> "English";
            case LANG_RU -> "Russian";
            default -> "Unknown";
        };
    }
    
    private String buildCacheKey(String sourceLang, String targetLang, ProductTranslation source) {
        return sourceLang + "_" + targetLang + "_" + 
               (source.name != null ? source.name.hashCode() : 0) + "_" +
               (source.description != null ? source.description.hashCode() : 0);
    }
    
    private Map<String, String> productTranslationToMap(ProductTranslation pt) {
        Map<String, String> map = new HashMap<>();
        map.put("name", pt.name);
        map.put("description", pt.description);
        map.put("shortDescription", pt.shortDescription);
        map.put("form", pt.form);
        map.put("flavor", pt.flavor);
        // Categories stored as JSON array string
        try {
            map.put("categories", objectMapper.writeValueAsString(pt.categories));
        } catch (Exception e) {
            map.put("categories", "[]");
        }
        return map;
    }
    
    private ProductTranslation mapToProductTranslation(Map<String, String> map) {
        ProductTranslation pt = new ProductTranslation();
        pt.name = map.get("name");
        pt.description = map.get("description");
        pt.shortDescription = map.get("shortDescription");
        pt.form = map.get("form");
        pt.flavor = map.get("flavor");
        // Parse categories from JSON
        try {
            String categoriesJson = map.get("categories");
            if (categoriesJson != null) {
                pt.categories = objectMapper.readValue(categoriesJson, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception e) {
            pt.categories = new ArrayList<>();
        }
        return pt;
    }
    
    /**
     * Container for translatable product fields
     */
    public static class ProductTranslation {
        public String name;
        public String description;
        public String shortDescription;
        public String benefitSnippet;
        public List<String> categories;
        public String form;
        public String flavor;
        public List<FaqItem> faq;
        
        public ProductTranslation() {
            this.categories = new ArrayList<>();
            this.faq = new ArrayList<>();
        }
        
        public static class FaqItem {
            public String q;
            public String a;
            
            public FaqItem() {}
            
            public FaqItem(String q, String a) {
                this.q = q;
                this.a = a;
            }
        }
    }
}
