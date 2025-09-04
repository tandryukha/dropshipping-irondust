package com.irondust.search.service;

import com.irondust.search.model.EnrichedProduct;
import com.irondust.search.model.RawProduct;
import com.irondust.search.service.enrichment.EnrichmentPipeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DietTagsVeganNegationTest {

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
    public void nonVeganTextShouldNotTagVegan() {
        String desc = "High-quality whey isolate. Non-vegan formula.";
        RawProduct raw = makeRaw("wc_T1", "Test Whey Isolate", desc, Map.of());

        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        List<String> tags = out.getDiet_tags();
        assertTrue(tags == null || !tags.contains("vegan"), "'Non-vegan' text must not set vegan tag");
    }

    @Test
    public void attributeNoShouldOverrideTextMentions() {
        String desc = "Protein suitable for athletes. Vegan protein mention in SEO text.";
        RawProduct raw = makeRaw(
                "wc_T2",
                "Protein Powder",
                desc,
                Map.of("attr_pa_kas-see-on-veganisobralik", List.of("ei"))
        );

        EnrichmentPipeline p = new EnrichmentPipeline();
        EnrichedProduct out = p.enrich(raw);

        List<String> tags = out.getDiet_tags();
        assertTrue(tags == null || !tags.contains("vegan"), "Explicit attribute 'ei/no' must block vegan tag");
    }
}


