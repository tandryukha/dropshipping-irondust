package com.irondust.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class TranslationServiceTest {

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
    public void englishValidationShouldIgnoreOUAndPass() throws Exception {
        TranslationService svc = new TranslationService(null, new ObjectMapper());

        TranslationService.ProductTranslation source = new TranslationService.ProductTranslation();
        source.name = "ICONFIT Capsules Zinc N90";
        source.description = """
                <article>
                <p><strong>Toote nimetus:</strong><br>ICONFIT Capsules Zinc N90</p>
                <p><strong>Vorm:</strong><br>Taimsed kapslid, 90 tk purgis. Iga kapsel sisaldab 25 mg tsinki kergesti omastatavas vormis (tsinktsitraat). Ühest purgist piisab 90 päevaks (1 kapsel päevas).</p>
                <p><strong>Koostis ja kasutussoovitused:</strong></p>
                <p><strong>Tootja:</strong><br>Eesti, ICONFIT OÜ</p>
                </article>
                """;

        TranslationService.ProductTranslation outEn = new TranslationService.ProductTranslation();
        outEn.name = "ICONFIT Capsules Zinc N90";
        outEn.description = """
                <article>
                <p><strong>Manufacturer:</strong><br>Estonia, ICONFIT OÜ</p>
                </article>
                """;

        boolean flagged = invokeLooksMistranslated(svc, TranslationService.LANG_EST, TranslationService.LANG_EN, source, outEn);
        assertFalse(flagged, "EN translation should not be flagged due to 'OÜ' legal token");
    }

    @Test
    public void russianValidationShouldPassWithCyrillic() throws Exception {
        TranslationService svc = new TranslationService(null, new ObjectMapper());

        TranslationService.ProductTranslation source = new TranslationService.ProductTranslation();
        source.name = "ICONFIT Capsules Zinc N90";
        source.description = """
                <article>
                <p><strong>Toote nimetus:</strong><br>ICONFIT Capsules Zinc N90</p>
                <p><strong>Vorm:</strong><br>Taimsed kapslid, 90 tk purgis. Iga kapsel sisaldab 25 mg tsinki.</p>
                </article>
                """;

        TranslationService.ProductTranslation outRu = new TranslationService.ProductTranslation();
        outRu.name = "ICONFIT Capsules Zinc N90";
        outRu.description = """
                <article>
                <p><strong>Производитель:</strong><br>Эстония, ICONFIT OÜ</p>
                </article>
                """;

        boolean flagged = invokeLooksMistranslated(svc, TranslationService.LANG_EST, TranslationService.LANG_RU, source, outRu);
        assertFalse(flagged, "RU translation with Cyrillic should not be flagged");
    }
}


