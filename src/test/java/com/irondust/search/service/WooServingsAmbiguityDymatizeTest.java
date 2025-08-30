package com.irondust.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.irondust.search.model.EnrichedProduct;
import com.irondust.search.model.RawProduct;
import com.irondust.search.service.enrichment.EnrichmentPipeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WooServingsAmbiguityDymatizeTest {
    private JsonNode buildWooFixture() {
        ObjectMapper om = new ObjectMapper();
        ObjectNode root = om.createObjectNode();
        root.put("id", 29581L);
        root.put("type", "simple");
        root.put("slug", "dymatize-bcaa-2200-capsules-400");
        root.put("name", "Dymatize BCAA 2200 caps");
        root.put("permalink", "https://www.irondust.eu/komplekssed-aminohapped/dymatize-bcaa-2200-capsules-400/");
        // Minimal HTML description containing the critical unit cues
        String desc = "<p><strong>Dymatize BCAA 2200 capsules</strong></p>" +
                "<p><strong>Form:</strong> Capsules (400 capsules / 2200 mg per serving)</p>" +
                "<p>Recommended daily dosage: Take 4 capsules daily.</p>";
        root.put("description", desc);

        // Attributes: include a count taxonomy to hint capsules packaging (400)
        ArrayNode attrs = om.createArrayNode();
        ObjectNode a1 = om.createObjectNode();
        a1.put("taxonomy", "pa_tablettide-arv");
        ArrayNode terms = om.createArrayNode();
        ObjectNode t = om.createObjectNode();
        t.put("slug", "400");
        t.put("name", "400");
        terms.add(t);
        a1.set("terms", terms);
        attrs.add(a1);
        root.set("attributes", attrs);

        // Categories/images minimally present
        root.set("categories", om.createArrayNode());
        root.set("images", om.createArrayNode());

        // Prices block minimally present to satisfy parser (values not used by this test)
        ObjectNode prices = om.createObjectNode();
        prices.put("price", "3290");
        prices.put("currency_code", "EUR");
        root.set("prices", prices);
        return root;
    }

    @Test
    public void dymatizeBCAA2200ShouldNotEmitServingsUnitAmbiguity() {
        JsonNode json = buildWooFixture();
        RawProduct raw = RawProduct.fromJsonNode(json);
        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        // Expectation: no UNIT_AMBIGUITY on 'servings' for capsules packaging
        boolean hasAmbiguityServings = out.getWarnings() != null && out.getWarnings().stream()
                .anyMatch(w -> w.contains("UNIT_AMBIGUITY") && w.contains("servings"));
        assertFalse(hasAmbiguityServings, "should not emit UNIT_AMBIGUITY for servings on wc_29581");

        // Additionally, ensure unit_count/servings are plausible and not misparsed as 2200
        if (out.getUnit_count() != null) {
            assertTrue(out.getUnit_count() >= 30 && out.getUnit_count() <= 600,
                    "unit_count should be in plausible packaging range when present");
            assertNotEquals(2200, out.getUnit_count());
        }
        if (out.getServings() != null) {
            assertTrue(out.getServings() >= 30 && out.getServings() <= 600,
                    "servings should be in plausible packaging range when present");
            assertNotEquals(2200, out.getServings());
        }
    }
}
