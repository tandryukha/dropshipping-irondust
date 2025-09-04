package com.irondust.search.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TitleUtilsTest {

    @Test
    public void stripsTrailingDashAndSpaces() {
        String in = "XTEND EAA 40 servings Tropical —  ";
        String out = TitleUtils.sanitizeTitle(in);
        assertEquals("XTEND EAA 40 servings Tropical", out);
    }

    @Test
    public void stripsLeadingSeparators() {
        String in = " —  |  :  C4 Original 30 servings";
        String out = TitleUtils.sanitizeTitle(in);
        assertEquals("C4 Original 30 servings", out);
    }

    @Test
    public void collapsesSpacesAndKeepsMiddleDashes() {
        String in = "Whey  Protein  Isolate  –  Vanilla";
        String out = TitleUtils.sanitizeTitle(in);
        assertEquals("Whey Protein Isolate – Vanilla", out);
    }

    @Test
    public void emptyAfterCleanFallsBackToTrimmedOriginal() {
        String in = "   —  ";
        String out = TitleUtils.sanitizeTitle(in);
        assertEquals("—", out, "fallback retains trimmed original when everything gets stripped");
    }
    
    @Test
    public void deduplicatesFinalWordCaseInsensitive() {
        String in = "MST Citrulline RAW 300g Unflavored unflavored";
        String out = TitleUtils.sanitizeTitle(in);
        assertEquals("MST Citrulline RAW 300g Unflavored", out);
    }
    @Test
    public void trimsAndCollapsesSpaces() {
        String s = TitleUtils.sanitizeTitle("  Pre‑workout   —   Cellucor  ");
        assertEquals("Pre‑workout — Cellucor", s);
    }

    @Test
    public void stripsLeadingTrailingSeparators() {
        String s = TitleUtils.sanitizeTitle("— |  Whey 1 kg  | —");
        assertEquals("Whey 1 kg", s);
    }

    @Test
    public void keepsInnerDashesIntact() {
        String s = TitleUtils.sanitizeTitle("C4 Original — Cellucor");
        assertEquals("C4 Original — Cellucor", s);
    }
}
