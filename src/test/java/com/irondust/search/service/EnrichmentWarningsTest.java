package com.irondust.search.service;

import com.irondust.search.model.EnrichedProduct;
import com.irondust.search.model.RawProduct;
import com.irondust.search.service.enrichment.EnrichmentPipeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces warning-related edge cases observed in reingest.
 */
public class EnrichmentWarningsTest {

    private RawProduct rawCaps(String id, String name, String text,
                               Integer unitCount, Integer unitsPerServing, Double unitMassG) {
        RawProduct r = new RawProduct();
        r.setId(id);
        r.setName(name);
        r.setDescription(text != null ? text : "");
        String st = (name != null ? name + " " : "") + (text != null ? text : "");
        r.setSearch_text(st);
        java.util.Map<String, java.util.List<String>> attrs = new java.util.LinkedHashMap<>();
        if (unitCount != null) attrs.put("attr_pa_tablettide-arv", java.util.List.of(String.valueOf(unitCount)));
        r.setDynamic_attrs(attrs);
        return r;
    }

    @Test
    public void capsulesWithUnitsPerServingShouldNotEmitMissingServingsWarningWhenDerivable() {
        // Given: capsules, units_per_serving in text, and unit_count attribute
        String name = "NOW Taurine 500mg 100 veg caps";
        String text = "Per serving 1 capsule (500 mg). 100 tablets per bottle.";
        RawProduct raw = rawCaps("wc_bug1", name, text, 100, 1, 0.5);

        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        // Expect: servings derived to ~100, no missing-critical 'servings' warning
        assertNotNull(out.getServings(), "servings should be present");
        boolean missingServings = out.getWarnings() != null && out.getWarnings().stream().anyMatch(w -> w.contains("MISSING_CRITICAL") && w.contains("servings"));
        assertFalse(missingServings, "should not warn about missing servings when derivable from units_per_serving and unit_count");
    }

    @Test
    public void softgelsShouldNotWarnNetWeightWhenUnitMassAndCountAvailable() {
        String name = "MST Omega 3 Selected 60 softgels";
        String text = "Per softgel 1000 mg. 60 softgels.";
        RawProduct raw = rawCaps("wc_bug2", name, text, 60, 1, 1.0);

        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        assertNotNull(out.getNet_weight_g(), "net_weight_g should be derivable from unit_count x unit_mass_g");
        boolean missingWeight = out.getWarnings() != null && out.getWarnings().stream().anyMatch(w -> w.contains("MISSING_CRITICAL") && w.contains("net_weight_g"));
        assertFalse(missingWeight, "should not warn about missing net_weight_g when derivable from unit data");
    }
}


