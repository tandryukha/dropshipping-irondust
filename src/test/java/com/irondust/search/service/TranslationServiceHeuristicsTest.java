package com.irondust.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class TranslationServiceHeuristicsTest {

    private boolean invokeLooksMistranslated(TranslationService svc,
                                             String sourceLang,
                                             String targetLang,
                                             TranslationService.ProductTranslation source,
                                             TranslationService.ProductTranslation out) throws Exception {
        Method m = TranslationService.class.getDeclaredMethod(
                "looksMistranslated",
                String.class, String.class,
                TranslationService.ProductTranslation.class,
                TranslationService.ProductTranslation.class);
        m.setAccessible(true);
        Object res = m.invoke(svc, sourceLang, targetLang, source, out);
        return (Boolean) res;
    }

    @Test
    public void ruTranslationWithoutCyrillicButChangedAndNoEtMarkersShouldPass() throws Exception {
        TranslationService svc = new TranslationService(null, new ObjectMapper());

        TranslationService.ProductTranslation source = new TranslationService.ProductTranslation();
        source.name = "ICONFIT Kapslid";
        source.description = "Toote nimetus: Tsink kapslid";

        TranslationService.ProductTranslation outRu = new TranslationService.ProductTranslation();
        outRu.name = "ICONFIT Caps";
        outRu.description = "Translated product for cardiovascular health and daily use";

        boolean flagged = invokeLooksMistranslated(svc, TranslationService.LANG_EST, TranslationService.LANG_RU, source, outRu);
        assertFalse(flagged, "RU translation heuristics should allow when changed and no ET markers, even if ASCII-only");
    }

    @Test
    public void etTranslationShouldFlagIfClearlyEnglishAndNoEtMarkers() throws Exception {
        TranslationService svc = new TranslationService(null, new ObjectMapper());

        TranslationService.ProductTranslation source = new TranslationService.ProductTranslation();
        source.name = "Now Foods";
        source.description = "Product";

        TranslationService.ProductTranslation outEt = new TranslationService.ProductTranslation();
        outEt.name = "Now Foods";
        outEt.description = "The daily supplement with ingredients and servings";

        boolean flagged = invokeLooksMistranslated(svc, TranslationService.LANG_EN, TranslationService.LANG_EST, source, outEt);
        assertTrue(flagged, "ET translation should be flagged if it looks English and lacks Estonian markers");
    }
}


