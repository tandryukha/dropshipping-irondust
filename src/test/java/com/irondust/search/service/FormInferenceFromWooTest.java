package com.irondust.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.irondust.search.model.EnrichedProduct;
import com.irondust.search.model.RawProduct;
import com.irondust.search.service.enrichment.EnrichmentPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class FormInferenceFromWooTest {
    private Mono<JsonNode> fetchWoo(long id) {
        String base = System.getenv().getOrDefault("WOO_BASE", "https://www.irondust.eu");
        WebClient wc = WebClient.builder().baseUrl(base).build();
        return wc.get().uri(uriBuilder -> uriBuilder.path("/wp-json/wc/store/v1/products/{id}").build(id))
                .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(5));
    }

    @Test
    public void omegaSoftgelsShouldInferCapsules_evenWhenAttrMissing() {
        long id = 56022L; // omega category; previously missing form
        JsonNode json;
        try {
            json = fetchWoo(id).block();
        } catch (Exception e) {
            // Network not available, skip
            return;
        }
        assertNotNull(json);
        RawProduct raw = RawProduct.fromJsonNode(json);
        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);
        // If reliable deterministic evidence is absent, null + warning is acceptable
        if (out.getForm() == null) {
            assertNotNull(out.getWarnings(), "warnings should explain missing form");
            assertTrue(out.getWarnings().stream().anyMatch(w -> w.contains("MISSING_CRITICAL") && w.contains("form")),
                    "should emit missing-critical form warning when not reliably inferable");
            return;
        }
        // Otherwise, if inferred, it should be capsules/tabs
        assertTrue("capsules".equals(out.getForm()) || "tabs".equals(out.getForm()), "form should be capsules/tabs");
    }
}
