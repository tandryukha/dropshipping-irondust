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
        // Close to actual Woo payload for wc_31476
        String json = "{\n" +
                "  \"id\": 50533,\n" +
                "  \"name\": \"MST Citrulline RAW 300g Maitsestamata\",\n" +
                "  \"permalink\": \"https://example/p/50533\",\n" +
                "  \"images\": [],\n" +
                "  \"categories\": [],\n" +
                "  \"attributes\": [{\n" +
                "    \"taxonomy\": \"pa_tootja\",\n" +
                "    \"terms\": [{\n" +
                "      \"slug\": \"mst-nutrition\",\n" +
                "      \"name\": \"MST Nutrition®\"\n" +
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
        assertTrue(d.getDisplay_title().endsWith("— MST Nutrition®"));
        assertTrue(d.getDisplay_title().startsWith("Citrulline RAW"));
    }
}


