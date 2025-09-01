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

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;

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
    private static final int REQUEST_TIMEOUT_SEC = 60;
    private static final long TRANSLATION_CACHE_TTL_MS = Duration.ofDays(365).toMillis();
    // Persistent cache (single-node) for translations
    private static final Object PERSIST_LOCK = new Object();
    private static final File PERSIST_FILE = new File("tmp/translation-cache.json");
    private static Map<String, PersistEntry> PERSISTENT_CACHE = new LinkedHashMap<>();
    
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
            // Expire after configured TTL (1 year)
            return System.currentTimeMillis() - timestamp > TRANSLATION_CACHE_TTL_MS;
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
        
        // Translate to ALL supported languages (including source) to ensure consistency
        Map<String, ProductTranslation> result = new HashMap<>();

        return Mono.just(SUPPORTED_LANGUAGES)
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
                .onErrorReturn(result); // Return partial on error
    }
    
    /**
     * Translates text from source to target language using OpenAI API.
     * Handles product-specific terminology and maintains consistency.
     */
    private Mono<ProductTranslation> translateToLanguage(
            String sourceLang, 
            String targetLang, 
            ProductTranslation source) {
        
        // Skip same-language translations: return source as-is
        if (Objects.equals(sourceLang, targetLang)) {
            return Mono.just(source);
        }

        // Check cache first
        String cacheKey = buildCacheKey(sourceLang, targetLang, source);
        TranslationCache cached = translationCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return Mono.just(mapToProductTranslation(cached.translations))
                    .map(tr -> {
                        if (looksMistranslated(sourceLang, targetLang, source, tr)) {
                            if (tr.warnings == null) tr.warnings = new ArrayList<>();
                            tr.warnings.add("translation_validation_failed " + sourceLang + "→" + targetLang + " (cache)");
                        }
                        return tr;
                    });
        }
        // Check persistent cache
        ProductTranslation persisted = getFromPersistent(cacheKey);
        if (persisted != null) {
            translationCache.put(cacheKey, new TranslationCache(productTranslationToMap(persisted)));
            return Mono.just(persisted)
                    .map(tr -> {
                        if (looksMistranslated(sourceLang, targetLang, source, tr)) {
                            if (tr.warnings == null) tr.warnings = new ArrayList<>();
                            tr.warnings.add("translation_validation_failed " + sourceLang + "→" + targetLang + " (persist)");
                        }
                        return tr;
                    });
        }
        
        // Build translation request
        String systemPrompt = buildSystemPrompt(sourceLang, targetLang);
        String userContent = buildTranslationContent(source);

        // Log payload sizes and rough token estimate
        long sysChars = systemPrompt != null ? systemPrompt.length() : 0;
        long userChars = userContent != null ? userContent.length() : 0;
        long sysTok = estimateTokens(systemPrompt);
        long userTok = estimateTokens(userContent);
        long approxTotalTok = sysTok + userTok + 200; // overhead cushion
        log.info("Translate request → {}→{} sysChars={} (~{} tok) userChars={} (~{} tok) approxTotalTok~{} model={} timeout={}s",
                sourceLang, targetLang, sysChars, sysTok, userChars, userTok, approxTotalTok, model, REQUEST_TIMEOUT_SEC);
        
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("temperature", 0.1); // Low temperature for consistency
        request.put("max_tokens", 6000);
        
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
        
        Mono<ProductTranslation> primary = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request.toString())
                .retrieve()
                .onStatus(status -> status.isError(), resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> {
                                    log.error("OpenAI translation HTTP {}: {}", resp.statusCode().value(), truncateForLog(body));
                                    return Mono.error(new RuntimeException("OpenAI error " + resp.statusCode().value()));
                                })
                )
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)).jitter(0.5))
                .map(response -> parseTranslationResponse(response, source))
                .doOnSuccess(translation -> {
                    // Cache the result
                    Map<String, String> translationMap = productTranslationToMap(translation);
                    translationCache.put(cacheKey, new TranslationCache(translationMap));
                    putToPersistent(cacheKey, translationMap);
                })
                .doOnError(e -> log.error("OpenAI translation error ({}): {}", e.getClass().getSimpleName(), e.getMessage()))
                .onErrorReturn(source);

        // Validate language; if looks wrong, attach a warning and return first pass as-is (no retry)
        return primary.map(tr -> {
            if (tr != source && looksMistranslated(sourceLang, targetLang, source, tr)) {
                if (tr.warnings == null) tr.warnings = new ArrayList<>();
                tr.warnings.add("translation_validation_failed " + sourceLang + "→" + targetLang);
                log.warn("Translation validation failed for {}→{}; returning first pass without retry", sourceLang, targetLang);
            }
            return tr;
        });
    }
    
    private String buildSystemPrompt(String sourceLang, String targetLang) {
        String sourceLanguageName = getLanguageName(sourceLang);
        String targetLanguageName = getLanguageName(targetLang);
        
        String estSpecificGuidance = "";
        if (LANG_EST.equals(targetLang)) {
            estSpecificGuidance = """

            11. For Estonian, translate generic product terms into Estonian; keep brand and flavor unchanged.
                Examples: "whey protein" → "vadakuvalk"; "protein" → "valk"; "casein" → "kaseiin";
                "isolate" → "isolaat"; "concentrate" → "kontsentraat"; "creatine" → "kreatiin";
                "beta-alanine" → "beeta-alaniin"; "pre-workout" → "treeningueelne"; "capsules" → "kapslid"; "powder" → "pulber".
            """;
        }
        
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
            9. IMPORTANT: The final output must be entirely in the target language (%s). Do NOT leave text in the source language.
            10. Output ONLY a raw JSON object. Do NOT include markdown, code fences, or any commentary.
            %s
            
            Return a JSON object with these exact fields:
            {
                "name": "translated product name",
                "description": "translated description or null",
                "short_description": "translated short description or null",
                "benefit_snippet": "translated benefit snippet or null",
                "dosage_text": "translated dosage sentence or null",
                "timing_text": "translated timing sentence or null",
                "categories": ["translated", "category", "names"],
                "form": "translated form or null",
                "flavor": "translated flavor or null",
                "faq": [
                    {"q": "translated question", "a": "translated answer"},
                    ...
                ]
            }
            
            If a field is null or empty in the source, keep it null in the translation.
            """, sourceLanguageName, targetLanguageName, targetLanguageName, estSpecificGuidance);
    }

    // Entry persisted on disk
    private static class PersistEntry {
        public Map<String, String> t;
        public long ts;
    }

    static {
        synchronized (PERSIST_LOCK) {
            try {
                if (PERSIST_FILE.exists()) {
                    byte[] bytes = Files.readAllBytes(PERSIST_FILE.toPath());
                    if (bytes.length > 0) {
                        // read as Map<String, PersistEntry>
                        ObjectMapper om = new ObjectMapper();
                        Map<String, Object> raw = om.readValue(bytes, Map.class);
                        Map<String, PersistEntry> loaded = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> e : raw.entrySet()) {
                            if (!(e.getValue() instanceof Map<?, ?> m)) continue;
                            PersistEntry pe = new PersistEntry();
                            Object ts = m.get("ts");
                            Object t = m.get("t");
                            pe.ts = (ts instanceof Number) ? ((Number) ts).longValue() : System.currentTimeMillis();
                            if (t instanceof Map<?, ?> tm) {
                                Map<String, String> conv = new LinkedHashMap<>();
                                for (Map.Entry<?, ?> te : tm.entrySet()) {
                                    conv.put(String.valueOf(te.getKey()), te.getValue() != null ? String.valueOf(te.getValue()) : null);
                                }
                                pe.t = conv;
                            } else {
                                pe.t = new LinkedHashMap<>();
                            }
                            loaded.put(e.getKey(), pe);
                        }
                        PERSISTENT_CACHE = loaded;
                    }
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(TranslationService.class).warn("Failed to load translation cache: {}", e.toString());
                PERSISTENT_CACHE = new LinkedHashMap<>();
            }
        }
    }

    public void clearPersistentCache() {
        synchronized (PERSIST_LOCK) {
            PERSISTENT_CACHE.clear();
            try { Files.deleteIfExists(PERSIST_FILE.toPath()); } catch (IOException e) {
                LoggerFactory.getLogger(TranslationService.class).warn("Failed to delete translation cache file: {}", e.toString());
            }
            translationCache.clear();
        }
    }

    private static void ensurePersistDir() {
        File dir = PERSIST_FILE.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
    }

    private static void savePersistentCache(Map<String, PersistEntry> data) {
        ensurePersistDir();
        try {
            ObjectMapper om = new ObjectMapper();
            byte[] bytes = om.writeValueAsBytes(data);
            Files.write(PERSIST_FILE.toPath(), bytes);
        } catch (IOException e) {
            LoggerFactory.getLogger(TranslationService.class).warn("Failed to save translation cache: {}", e.toString());
        }
    }

    private ProductTranslation getFromPersistent(String cacheKey) {
        synchronized (PERSIST_LOCK) {
            PersistEntry pe = PERSISTENT_CACHE.get(cacheKey);
            if (pe == null) return null;
            if (System.currentTimeMillis() - pe.ts > TRANSLATION_CACHE_TTL_MS) return null;
            return mapToProductTranslation(pe.t);
        }
    }

    private void putToPersistent(String cacheKey, Map<String, String> tmap) {
        synchronized (PERSIST_LOCK) {
            PersistEntry pe = new PersistEntry();
            pe.t = new LinkedHashMap<>(tmap);
            pe.ts = System.currentTimeMillis();
            PERSISTENT_CACHE.put(cacheKey, pe);
            savePersistentCache(PERSISTENT_CACHE);
        }
    }

    private boolean looksMistranslated(String sourceLang, String targetLang, ProductTranslation source, ProductTranslation out) {
        String outDesc = out.description != null ? out.description : "";
        String outName = out.name != null ? out.name : "";
        String outCombined = (outName + " \n" + outDesc).toLowerCase();
        // Ignore legal entity tokens like OÜ / Osaühing when validating language
        outCombined = stripLegalEntityTokens(outCombined);

        String srcDesc = source.description != null ? source.description : "";
        String srcName = source.name != null ? source.name : "";
        String srcCombined = (srcName + " \n" + srcDesc).toLowerCase();
        srcCombined = stripLegalEntityTokens(srcCombined);

        // Quick difference check to reduce false positives when brand/numerical content dominates
        double changeRatio = stringChangeRatio(srcCombined, outCombined);

        if (LANG_EN.equals(targetLang)) {
            // English should not contain Cyrillic or strong Estonian markers
            if (containsCyrillic(outCombined)) return true;
            if (containsEstonianMarkers(outCombined)) return true;
            // If no clear markers but text changed sufficiently, accept
            if (changeRatio >= 0.25) return false;
        } else if (LANG_RU.equals(targetLang)) {
            // Prefer Cyrillic, but accept if the text changed and no Estonian markers remain
            if (containsCyrillic(outCombined)) return false;
            if (changeRatio >= 0.20 && !containsEstonianMarkers(outCombined)) return false;
            return true;
        } else if (LANG_EST.equals(targetLang)) {
            // Estonian should not contain Cyrillic or strong English-only markers
            if (containsCyrillic(outCombined)) return true;
            if (containsEstonianMarkers(outCombined)) return false;
            // If output doesn't look English and changed enough, accept as Estonian even without diacritics
            if (!looksEnglish(outCombined) && changeRatio >= 0.25) return false;
            return true;
        }

        // If target differs from source and output equals input for description, suspicious
        if (!Objects.equals(sourceLang, targetLang)) {
            if (!srcDesc.isBlank() && srcDesc.equals(out.description)) {
                // But allow equality if name changed significantly
                if (stringChangeRatio(srcName, outName) < 0.25) return true;
            }
        }
        return false;
    }

    /**
     * Heuristic to detect if text is likely English.
     */
    private boolean looksEnglish(String text) {
        if (text == null) return false;
        String t = text;
        // Common English stopwords/bigrams
        if (t.contains(" the ") || t.contains(" and ") || t.contains(" with ") || t.contains(" from ") || t.contains(" for ")
                || t.contains(" ingredients") || t.contains(" servings") || t.contains(" daily")) {
            return true;
        }
        // No diacritics and predominately ASCII letters can be a hint
        String letters = t.replaceAll("[^A-Za-z]", "");
        if (letters.length() >= 20 && letters.matches("[A-Za-z]{20,}")) return true;
        return false;
    }

    private String stripLegalEntityTokens(String text) {
        if (text == null) return null;
        String t = text;
        // Remove common legal forms that can include diacritics and cause false positives
        // Use Unicode-aware boundaries: not preceded/followed by a letter
        t = t.replaceAll("(?iu)(?<!\\p{L})(oü|osaühing)(?!\\p{L})", "");
        // Collapse extra whitespace introduced by removals
        t = t.replaceAll("\\s{2,}", " ").trim();
        return t;
    }

    private double stringChangeRatio(String a, String b) {
        if (a == null) a = ""; if (b == null) b = "";
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 0.0;
        int common = longestCommonSubsequenceLength(a, b);
        int diff = max - common;
        return diff / (double) Math.max(1, max);
    }

    // Simple LCS length for short-ish strings; caps for performance
    private int longestCommonSubsequenceLength(String a, String b) {
        int n = Math.min(a.length(), 2000);
        int m = Math.min(b.length(), 2000);
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                if (ca == b.charAt(j - 1)) dp[i][j] = dp[i - 1][j - 1] + 1;
                else dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        return dp[n][m];
    }

    private boolean containsCyrillic(String text) {
        if (text == null) return false;
        // Scan characters to detect Cyrillic block characters (more robust across regex engines)
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
            if (b == Character.UnicodeBlock.CYRILLIC || b == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY || b == Character.UnicodeBlock.CYRILLIC_EXTENDED_A || b == Character.UnicodeBlock.CYRILLIC_EXTENDED_B) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEstonianMarkers(String text) {
        if (text == null) return false;
        return text.indexOf('ä') >= 0 || text.indexOf('õ') >= 0 || text.indexOf('ö') >= 0 || text.indexOf('ü') >= 0
                || text.contains("toote nimetus") || text.contains("koostis") || text.contains("hoiatus")
                || text.contains("soovitatav") || text.contains("päevane annus") || text.contains("tootja:");
    }
    
    private String buildTranslationContent(ProductTranslation source) {
        Map<String, Object> content = new HashMap<>();
        content.put("name", safeTrim(source.name, 1000));
        content.put("description", sanitizeHtmlForModel(source.description));
        content.put("short_description", safeTrim(source.shortDescription, 4000));
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

    private String safeTrim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String sanitizeHtmlForModel(String html) {
        if (html == null) return null;
        String s = html;
        // Drop script/style blocks
        s = s.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        s = s.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        // Strip all tag attributes to reduce token bloat
        s = s.replaceAll("(?is)<([a-zA-Z0-9]+)([^>]*)>", "<$1>");
        // Collapse excessive whitespace
        s = s.replaceAll("\n{3,}", "\n\n");
        s = s.replaceAll("[\t\u000B\f\r]+", " ");
        // Hard cap length to keep request under context limits
        if (s.length() > 12000) s = s.substring(0, 11999) + "…";
        return s;
    }

    private String truncateForLog(String body) {
        if (body == null) return "";
        return body.length() > 1000 ? body.substring(0, 1000) + "…" : body;
    }

    private long estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // Rough heuristic: ~4 characters per token
        return Math.max(1, Math.round(text.length() / 4.0));
    }
    
    private ProductTranslation parseTranslationResponse(JsonNode response, ProductTranslation fallback) {
        try {
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode()) {
                return fallback;
            }

            JsonNode translationJson;
            if (content.isObject()) {
                translationJson = content;
            } else if (content.isTextual()) {
                String raw = content.asText();
                String jsonCandidate = extractJsonObject(raw);
                if (jsonCandidate == null) return fallback;
                translationJson = objectMapper.readTree(jsonCandidate);
            } else {
                return fallback;
            }
            
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
            // Optional dosage/timing lines
            result.dosageText = translationJson.path("dosage_text").isNull() ? null : translationJson.path("dosage_text").asText(null);
            result.timingText = translationJson.path("timing_text").isNull() ? null : translationJson.path("timing_text").asText(null);
            
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
     * Attempts to extract a clean JSON object string from model output.
     * Handles code fences and leading/trailing noise. Returns null if not found.
     */
    private String extractJsonObject(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // Strip code fences if present
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            if (start >= 0) {
                s = s.substring(start + 1);
            }
            int endFence = s.lastIndexOf("```");
            if (endFence > 0) {
                s = s.substring(0, endFence).trim();
            }
        }
        // If it already looks like JSON, try directly
        if (s.startsWith("{") && s.endsWith("}")) return s;
        // Try to locate the first '{' and the last '}' and parse that slice
        int first = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (first >= 0 && last > first) {
            String candidate = s.substring(first, last + 1);
            return candidate;
        }
        return null;
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
        map.put("dosage_text", pt.dosageText);
        map.put("timing_text", pt.timingText);
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
        pt.dosageText = map.get("dosage_text");
        pt.timingText = map.get("timing_text");
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
        public String dosageText;
        public String timingText;
        public List<String> categories;
        public String form;
        public String flavor;
        public List<FaqItem> faq;
        public List<String> warnings;
        
        public ProductTranslation() {
            this.categories = new ArrayList<>();
            this.faq = new ArrayList<>();
            this.warnings = new ArrayList<>();
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
