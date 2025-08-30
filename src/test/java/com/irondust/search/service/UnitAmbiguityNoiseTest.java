package com.irondust.search.service;

import com.irondust.search.model.EnrichedProduct;
import com.irondust.search.model.RawProduct;
import com.irondust.search.service.enrichment.EnrichmentPipeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UnitAmbiguityNoiseTest {

    private RawProduct makeRawWithAttrs(String id, String name, String desc, Map<String, List<String>> attrs) {
        RawProduct r = new RawProduct();
        r.setId(id);
        r.setName(name);
        r.setDescription(desc != null ? desc : "");
        String st = (name != null ? name + " " : "") + (desc != null ? desc : "");
        r.setSearch_text(st);
        r.setDynamic_attrs(attrs);
        return r;
    }

    @Test
    public void correctingNetWeightFromPerServingShouldNotEmitUnitAmbiguity() {
        // Woo mis-entered net_weight_g as 5 (per serving), text clearly says 5 g per serving and 60 servings.
        Map<String, List<String>> attrs = Map.of(
                "attr_pa_grammide-arv", List.of("5")
        );
        String desc = "Per serving 5 g. Pakend: 60 portsjonit.";
        RawProduct raw = makeRawWithAttrs("wc_noise1", "Test Powder 300g", desc, attrs);

        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        assertNotNull(out.getServing_size_g(), "serving_size_g parsed");
        assertNotNull(out.getServings(), "servings parsed");
        assertNotNull(out.getNet_weight_g(), "net_weight_g present");
        assertTrue(out.getNet_weight_g() > 100.0, "net_weight_g should be > 100g after correction");
        boolean hasUnitAmbiguity = out.getWarnings() != null && out.getWarnings().stream().anyMatch(w -> w.contains("UNIT_AMBIGUITY") && w.contains("net_weight_g"));
        assertFalse(hasUnitAmbiguity, "should suppress unit ambiguity warning for clear derivation from serving_size x servings");
    }

    @Test
    public void correctingServingsToDerivedValueShouldNotEmitUnitAmbiguity() {
        // Woo mis-entered servings as 5; weight 300g in attrs; text serving size 5 g.
        Map<String, List<String>> attrs = Map.of(
                "attr_pa_portsjonite-arv", List.of("5"),
                "attr_pa_grammide-arv", List.of("300")
        );
        String desc = "Serving size 5 g per serving.";
        RawProduct raw = makeRawWithAttrs("wc_noise2", "Test Powder 300g", desc, attrs);

        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        // Focus on noise suppression rather than exact derived value here
        boolean hasUnitAmbiguity = out.getWarnings() != null && out.getWarnings().stream().anyMatch(w -> w.contains("UNIT_AMBIGUITY") && w.contains("servings"));
        assertFalse(hasUnitAmbiguity, "should suppress unit ambiguity warning for clear net_weight/serving_size derivation");
    }
}


