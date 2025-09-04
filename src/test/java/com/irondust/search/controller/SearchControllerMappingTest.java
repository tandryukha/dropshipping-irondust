package com.irondust.search.controller;

import com.irondust.search.config.VectorProperties;
import com.irondust.search.model.ProductDoc;
import com.irondust.search.service.HybridSearchService;
import com.irondust.search.service.MeiliService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SearchControllerMappingTest {

    // Lightweight stubs
    static class NoopMeili extends MeiliService {
        public NoopMeili() { super(null, null, null); }
    }
    static class NoopHybrid extends HybridSearchService {
        public NoopHybrid() { super(new NoopMeili(), null, null, new VectorProperties()); }
    }

    @Test
    public void mapToProductDoc_sanitizesNameAndDisplayTitle() throws Exception {
        SearchController controller = new SearchController(new NoopMeili(), new NoopHybrid(), new VectorProperties());

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "wc_1");
        raw.put("type", "simple");
        raw.put("name", "  XTEND EAA 40 servings Tropical —  ");
        raw.put("display_title", " —  XTEND EAA 40 servings Tropical  — ");
        raw.put("price_cents", 2890);
        raw.put("currency", "EUR");
        raw.put("in_stock", true);
        raw.put("images", List.of("https://example/img.jpg"));

        ProductDoc d = new ProductDoc();

        Method m = SearchController.class.getDeclaredMethod("mapToProductDoc", Map.class, ProductDoc.class);
        m.setAccessible(true);
        m.invoke(controller, raw, d);

        assertEquals("XTEND EAA 40 servings Tropical", d.getName());
        assertEquals("XTEND EAA 40 servings Tropical", d.getDisplay_title());
    }
}


