package com.irondust.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irondust.search.model.EnrichedProduct;
import com.irondust.search.model.RawProduct;
import com.irondust.search.service.enrichment.EnrichmentPipeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EnrichmentPipelineTest {

    private RawProduct makeRaw(String id, String name, String desc, Map<String, List<String>> attrs) {
        RawProduct r = new RawProduct();
        r.setId(id);
        r.setName(name);
        r.setDescription(desc != null ? desc : "");
        // Simulate search_text like production pipeline does
        StringBuilder st = new StringBuilder();
        if (name != null) st.append(name).append(' ');
        if (desc != null) st.append(desc);
        r.setSearch_text(st.toString());
        r.setDynamic_attrs(attrs);
        return r;
    }

    @Test
    public void softgelsShouldInferCapsulesAndAvoidMissingForm() {
        String desc = "Toote nimetus: MST Omega 3 Selected 60 softgels Vorm: Pehmekapslid (softgels), 60 kapslit pudelis";
        RawProduct raw = makeRaw("wc_XX1", "MST Omega 3 Selected 60 softgels", desc, Map.of());

        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        assertEquals("capsules", out.getForm(), "softgels should map to capsules via heuristics");
        assertTrue(out.getWarnings() == null || out.getWarnings().stream().noneMatch(w -> w.contains("form")),
                "should not warn about missing form when softgels evidence present");
    }

    @Test
    public void shouldDeriveNetWeightFromServingSizeTimesServings() {
        String desc = "portsjon (5 g). Pakend: 60 portsjonit.";
        RawProduct raw = makeRaw("wc_XX2", "Test Powder 300g", desc, Map.of());

        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        // Expect serving_size_g ~5 and servings ~60 from text, and net_weight_g present (parsed or derived)
        assertNotNull(out.getServing_size_g(), "serving_size_g parsed");
        assertNotNull(out.getServings(), "servings parsed");
        assertNotNull(out.getNet_weight_g(), "net_weight_g present");
        assertTrue(out.getNet_weight_g() > 0.0, "net_weight_g should be positive");
        // Allow other non-critical warnings; core requirement is that net_weight_g is present
    }

    @Test
    public void capsulesShouldNotRequireServingsWhenNoUnitsPerServing() {
        RawProduct raw = makeRaw("wc_XX3", "NOW Taurine 500mg 100 veg caps", "", Map.of(
                "attr_pa_tablettide-arv", List.of("100")
        ));

        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        assertEquals("capsules", out.getForm());
        // Missing servings should not be flagged for count-based forms unless units_per_serving present
        boolean hasMissingServings = out.getWarnings() != null && out.getWarnings().stream().anyMatch(w -> w.contains("servings"));
        assertFalse(hasMissingServings, "capsules without units_per_serving should not warn about servings");
    }
}


