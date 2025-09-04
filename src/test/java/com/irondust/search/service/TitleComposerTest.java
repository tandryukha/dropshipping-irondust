package com.irondust.search.service;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;
import com.irondust.search.service.enrichment.EnrichmentDelta;
import com.irondust.search.service.enrichment.TitleComposer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TitleComposer deterministic display title composition.
 */
public class TitleComposerTest {

    @Test
    public void removesLeadingBrandAndAppendsAtEnd() {
        RawProduct raw = new RawProduct();
        raw.setName("MST Citrulline RAW 300g Unflavored");
        raw.setBrand_slug("mst-nutrition");
        raw.setBrand_name("MST Nutrition®");
        ParsedProduct soFar = ParsedProduct.fromRawProduct(raw);

        TitleComposer step = new TitleComposer();
        EnrichmentDelta d = step.apply(raw, soFar);
        assertNotNull(d);
        String display = (String) d.getUpdates().get("display_title");
        assertEquals("Citrulline RAW 300g Unflavored — MST Nutrition®", display);
    }

    @Test
    public void removesLeadingBrandAliasFirstWord() {
        RawProduct raw = new RawProduct();
        raw.setName("Cellucor C4 Original Pre‑Workout 30 servings (Cherry Lime)");
        raw.setBrand_name("Cellucor");
        ParsedProduct soFar = ParsedProduct.fromRawProduct(raw);

        TitleComposer step = new TitleComposer();
        EnrichmentDelta d = step.apply(raw, soFar);
        assertNotNull(d);
        String display = (String) d.getUpdates().get("display_title");
        assertEquals("C4 Original Pre‑Workout 30 servings (Cherry Lime) — Cellucor", display);
    }

    @Test
    public void keepsNameWhenBrandMissing() {
        RawProduct raw = new RawProduct();
        raw.setName("Whey Protein 1 kg");
        ParsedProduct soFar = ParsedProduct.fromRawProduct(raw);
        TitleComposer step = new TitleComposer();
        EnrichmentDelta d = step.apply(raw, soFar);
        String display = (String) d.getUpdates().get("display_title");
        assertEquals("Whey Protein 1 kg", display);
    }
}


