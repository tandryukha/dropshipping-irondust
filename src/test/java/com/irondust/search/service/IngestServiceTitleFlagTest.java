package com.irondust.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irondust.search.dto.IngestDtos;
import com.irondust.search.model.EnrichedProduct;
import com.irondust.search.model.ProductDoc;
import com.irondust.search.service.enrichment.EnrichmentPipeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lightweight integration-style unit test:
 * verifies that when the pipeline runs with title composer enabled,
 * the resulting ProductDoc carries display_title.
 */
public class IngestServiceTitleFlagTest {

    @Test
    public void pipelineProducesDisplayTitleWhenEnabled() throws Exception {
        // Build a minimal Raw-like JSON payload
        String json = "{\n" +
                "  \"id\": 50533,\n" +
                "  \"name\": \"Cellucor C4 Original Pre-workout 30 servings (Cherry Lime)\",\n" +
                "  \"permalink\": \"https://example/p/50533\",\n" +
                "  \"images\": [],\n" +
                "  \"categories\": [],\n" +
                "  \"attributes\": [{\n" +
                "    \"taxonomy\": \"pa_tootja\",\n" +
                "    \"terms\": [{\n" +
                "      \"slug\": \"cellucor\",\n" +
                "      \"name\": \"Cellucor\"\n" +
                "    }]\n" +
                "  }]\n" +
                "}";

        var node = new ObjectMapper().readTree(json);
        var raw = com.irondust.search.model.RawProduct.fromJsonNode(node);
        var pipeline = new EnrichmentPipeline(true);
        EnrichedProduct enriched = pipeline.enrich(raw);
        assertNotNull(enriched.getDisplay_title(), "display_title should be populated when composer enabled");

        // Map to ProductDoc via IngestService.createProductDoc-like fields
        ProductDoc d = new ProductDoc();
        d.setId(enriched.getId());
        d.setName(enriched.getName());
        d.setBrand_name(enriched.getBrand_name());
        d.setDisplay_title(enriched.getDisplay_title());
        assertTrue(d.getDisplay_title().contains("Cellucor"));
    }
}


