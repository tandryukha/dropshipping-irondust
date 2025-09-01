package com.irondust.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irondust.search.config.VectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Embedding generator using OpenAI embeddings API.
 * Caches results in-memory during process lifetime to reduce cost.
 */
@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final String OPENAI_EMBED_URL = "https://api.openai.com/v1/embeddings";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final VectorProperties vectorProperties;
    private final String apiKey;

    // Simple process-lifetime cache: text -> embedding
    private final Map<String, float[]> cache = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return this.size() > 5000; // bounded cache
        }
    });

    public EmbeddingService(VectorProperties vectorProperties) {
        this.vectorProperties = vectorProperties;
        this.apiKey = System.getenv("OPENAI_API_KEY");
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public int embeddingDim() { return vectorProperties.getEmbeddingDim(); }

    public float[] embedText(String text) {
        if (text == null) text = "";
        String key = vectorProperties.getEmbeddingModel() + "\n" + text;
        float[] cached = cache.get(key);
        if (cached != null) return cached;
        try {
            long t0 = System.currentTimeMillis();
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", vectorProperties.getEmbeddingModel());
            req.put("input", text);
            String body = mapper.writeValueAsString(req);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_EMBED_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 300) {
                log.warn("Embedding API error status {}: {}", resp.statusCode(), resp.body());
                return new float[vectorProperties.getEmbeddingDim()];
            }
            Map<String, Object> parsed = mapper.readValue(resp.body(), new TypeReference<Map<String, Object>>(){});
            List<Map<String, Object>> data = (List<Map<String, Object>>) parsed.get("data");
            if (data == null || data.isEmpty()) return new float[vectorProperties.getEmbeddingDim()];
            Map<String, Object> first = data.get(0);
            List<Number> values = (List<Number>) first.get("embedding");
            float[] out = new float[values.size()];
            for (int i = 0; i < values.size(); i++) out[i] = values.get(i).floatValue();
            cache.put(key, out);
            long dt = System.currentTimeMillis() - t0;
            log.info("Embedding generated: model={} dim={} tokens~={}ms={}", vectorProperties.getEmbeddingModel(), out.length, text.length(), dt);
            return out;
        } catch (Exception e) {
            log.warn("Embedding error: {}", e.toString());
            return new float[vectorProperties.getEmbeddingDim()];
        }
    }
}


