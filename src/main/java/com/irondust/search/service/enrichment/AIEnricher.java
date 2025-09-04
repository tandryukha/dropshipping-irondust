package com.irondust.search.service.enrichment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irondust.search.model.ParsedProduct;
import com.irondust.search.model.RawProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.irondust.search.util.TokenAccounting;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Optional AI enricher. Runs once per product when enabled via environment variables.
 *
 * Env:
 *  - OPENAI_API_KEY: required to enable
 *  - OPENAI_MODEL: optional, default gpt-4o-mini
 *  - AI_ENRICH: if set to "true" (default true when key present), enable
 */
public class AIEnricher {
    private static final Logger log = LoggerFactory.getLogger(AIEnricher.class);
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String apiKey;
    private final String model;
    private static final long ENRICHMENT_CACHE_TTL_MS = 365L * 24 * 60 * 60 * 1000; // 1 year

    // Persistent cache (single-node) for enrichment responses
    private static final Object CACHE_LOCK = new Object();
    private static final File CACHE_FILE = new File("tmp/ai-enrichment-cache.json");
    private static final ObjectMapper STATIC_MAPPER = new ObjectMapper();
    private static Map<String, Map<String, Object>> PERSISTENT_CACHE = new LinkedHashMap<>();

    static {
        synchronized (CACHE_LOCK) {
            try {
                if (CACHE_FILE.exists()) {
                    byte[] bytes = Files.readAllBytes(CACHE_FILE.toPath());
                    if (bytes.length > 0) {
                        PERSISTENT_CACHE = STATIC_MAPPER.readValue(
                            bytes, new TypeReference<Map<String, Map<String, Object>>>() {}
                        );
                    }
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(AIEnricher.class).warn("Failed to load AI cache: {}", e.toString());
                PERSISTENT_CACHE = new LinkedHashMap<>();
            }
        }
    }

    private static void ensureCacheDir() {
        File dir = CACHE_FILE.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
    }

    private static void savePersistentCache() {
        ensureCacheDir();
        try {
            byte[] bytes = STATIC_MAPPER.writeValueAsBytes(PERSISTENT_CACHE);
            Files.write(CACHE_FILE.toPath(), bytes);
        } catch (IOException e) {
            LoggerFactory.getLogger(AIEnricher.class).warn("Failed to save AI cache: {}", e.toString());
        }
    }

    public static void clearPersistentCache() {
        synchronized (CACHE_LOCK) {
            PERSISTENT_CACHE.clear();
            try {
                Files.deleteIfExists(CACHE_FILE.toPath());
            } catch (IOException e) {
                LoggerFactory.getLogger(AIEnricher.class).warn("Failed to delete AI cache file: {}", e.toString());
            }
        }
    }

    private static Map<String, Object> getCached(String key) {
        synchronized (CACHE_LOCK) {
            Map<String, Object> v = PERSISTENT_CACHE.get(key);
            if (v == null) return null;
            Object tsObj = v.get("ai_enrichment_ts");
            long tsSec = 0L;
            if (tsObj instanceof Number n) tsSec = n.longValue();
            else {
                try { tsSec = Long.parseLong(String.valueOf(tsObj)); } catch (Exception ignored) {}
            }
            long tsMs = tsSec > 0 ? tsSec * 1000L : 0L;
            if (tsMs > 0 && System.currentTimeMillis() - tsMs > ENRICHMENT_CACHE_TTL_MS) return null;
            return v;
        }
    }

    private static void putCached(String key, Map<String, Object> value) {
        synchronized (CACHE_LOCK) {
            PERSISTENT_CACHE.put(key, value);
            savePersistentCache();
        }
    }

    public AIEnricher() {
        this.apiKey = System.getenv("OPENAI_API_KEY");
        String m = System.getenv("OPENAI_MODEL");
        this.model = (m == null || m.isBlank()) ? "gpt-4o-mini" : m;
    }

    public boolean isEnabled() {
        if (apiKey == null || apiKey.isBlank()) return false;
        String enabled = System.getenv("AI_ENRICH");
        return enabled != null && enabled.equalsIgnoreCase("true");
    }

    public Map<String, Object> enrich(RawProduct raw, ParsedProduct parsed) {
        try {
            // Cache key mode: default to raw-only so cached AI responses survive code changes
            String cacheKeyMode = System.getenv().getOrDefault("AI_CACHE_KEY_MODE", "raw");
            String inputForHashJson = "raw_parsed".equalsIgnoreCase(cacheKeyMode)
                    ? buildInputJson(raw, parsed)
                    : buildRawOnlyInputJson(raw);
            String inputHash = sha256Hex(inputForHashJson);
            String input = buildInputJson(raw, parsed);
            String cacheKey = model + ":v1:" + inputHash;

            // Cache lookup
            Map<String, Object> cached = getCached(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.info("AI cache hit → product={} key={}", raw.getId(), cacheKey);
                return cached;
            }

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", model);
            req.put("temperature", 0);
            req.put("response_format", Map.of("type", "json_object"));
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "You are a product enrichment engine. Reply STRICT JSON per schema."));
            messages.add(Map.of("role", "user", "content", buildPrompt(input)));
            req.put("messages", messages);

            String body = mapper.writeValueAsString(req);
            log.info("AI request → product={} model={} hash={}", raw.getId(), model, inputHash);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
            log.info("AI response ← product={} status={}", raw.getId(), resp.statusCode());
            if (resp.statusCode() >= 300) {
                log.warn("AI enrich failed status {}: {}", resp.statusCode(), resp.body());
                return Map.of();
            }
            Map<String, Object> parsedResp = mapper.readValue(resp.body(), new TypeReference<Map<String, Object>>(){});
            try {
                Object usageObj = parsedResp.get("usage");
                String usedModel = parsedResp.get("model") != null ? String.valueOf(parsedResp.get("model")) : model;
                if (usageObj instanceof Map<?, ?> u) {
                    Object p = u.get("prompt_tokens");
                    Object c = u.get("completion_tokens");
                    Object t = u.get("total_tokens");
                    long pTok = (p instanceof Number) ? ((Number) p).longValue() : 0L;
                    long cTok = (c instanceof Number) ? ((Number) c).longValue() : 0L;
                    long tTok = (t instanceof Number) ? ((Number) t).longValue() : 0L;
                    TokenAccounting.recordChatCompletionUsage(usedModel, pTok, cTok, tTok);
                }
            } catch (Exception ignored) {}
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsedResp.get("choices");
            if (choices == null || choices.isEmpty()) return Map.of();
            Map<String, Object> msg = (Map<String, Object>) ((Map<String, Object>) choices.get(0).get("message"));
            String content = String.valueOf(msg.get("content"));
            // content is a JSON string matching our schema
            Map<String, Object> out = mapper.readValue(content, new TypeReference<Map<String, Object>>(){});
            out.put("ai_input_hash", inputHash);
            out.put("ai_enrichment_ts", System.currentTimeMillis() / 1000);
            out.put("enrichment_version", 1);
            // Persist cache
            putCached(cacheKey, out);
            return out;
        } catch (Exception e) {
            log.warn("AI enrichment error: {}", e.toString());
            return Map.of();
        }
    }

    private String buildPrompt(String inputJson) {
        return "Given product JSON below, validate parsed fields and fill nulls; then generate UX fields and goal relevance scores. " +
               "Return ONLY this JSON object with keys fill, generate, safety_flags, conflicts, goal_scores.\n" +
               "Schema: { fill: {form, flavor, servings, servings_min, servings_max, serving_size_g, ingredients_key, goal_tags, diet_tags}, " +
               "generate: {benefit_snippet, faq: [{q,a}], synonyms_multi: {en:[], ru:[], et:[]}, dosage_text, timing_text}, safety_flags: [{flag,confidence,evidence}], " +
               "conflicts: [{field, det_value, ai_value, evidence}], " +
               "goal_scores: { preworkout: {score, confidence}, strength: {score, confidence}, endurance: {score, confidence}, lean_muscle: {score, confidence}, recovery: {score, confidence}, weight_loss: {score, confidence}, wellness: {score, confidence} } }.\n" +
               "Rules: Prefer explicit numeric evidence. You MAY use title, slug or SKU cues like '60caps', '60vcaps', '90 tablets' as evidence for servings. " +
               "For dosage_text output a single concise line like '1 scoop (≈12.5 g) daily' or '2 capsules per day'. No timing here. " +
               "For timing_text output a single concise line like 'Before or after workout' or 'With meals'. Do not repeat dosage. " +
               "If dosage/serving size is not explicitly stated, infer a reasonable typical value based on product type (e.g., creatine monohydrate powder often 3–5 g). " +
               "If the label communicates a range, prefer {servings_min, servings_max}. If both range and exact exist, prefer exact attribute value. " +
               "Only include a conflict when a deterministic value exists (det_value != null) AND you have a different value; if det_value is null, put your value under 'fill' only. " +
               "Use short evidence quotes. Max 160 chars for benefit_snippet. " +
               "For goal_scores, set score in [0.0,1.0] reflecting how well the product serves each goal; set confidence in [0.0,1.0]. " +
               "Bias: creatine → strength/endurance; pre-workout boosters → preworkout; multivitamins → wellness; fat-burners → weight_loss; protein → lean_muscle/recovery.\n" +
               "INPUT:" + inputJson;
    }

    private String buildRawOnlyInputJson(RawProduct raw) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", raw.getId());
        m.put("name", raw.getName());
        m.put("slug", raw.getSlug());
        m.put("sku", raw.getSku());
        m.put("search_text", raw.getSearch_text());
        m.put("brand", raw.getBrand_name());
        m.put("categories", raw.getCategories_names());
        m.put("attrs", raw.getDynamic_attrs());
        return mapper.writeValueAsString(m);
    }

    private String buildInputJson(RawProduct raw, ParsedProduct parsed) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", raw.getId());
        m.put("name", raw.getName());
        m.put("slug", raw.getSlug());
        m.put("sku", raw.getSku());
        m.put("search_text", raw.getSearch_text());
        m.put("brand", raw.getBrand_name());
        m.put("categories", raw.getCategories_names());
        m.put("attrs", raw.getDynamic_attrs());
        Map<String, Object> parsedCore = new LinkedHashMap<>();
        parsedCore.put("form", parsed.getForm());
        parsedCore.put("flavor", parsed.getFlavor());
        parsedCore.put("net_weight_g", parsed.getNet_weight_g());
        parsedCore.put("servings", parsed.getServings());
        parsedCore.put("servings_min", parsed.getServings_min());
        parsedCore.put("servings_max", parsed.getServings_max());
        parsedCore.put("serving_size_g", parsed.getServing_size_g());
        parsedCore.put("goal_tags", parsed.getGoal_tags());
        parsedCore.put("diet_tags", parsed.getDiet_tags());
        m.put("parsed", parsedCore);
        return mapper.writeValueAsString(m);
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}


