# Dosage and Timing Extraction

This page explains how dosage and timing are generated, localized, and used by the UI.

## Overview

- Backend enrichment outputs two concise strings per product:
  - `dosage_text`: single-sentence dosage guidance (no timing).
  - `timing_text`: single-sentence timing guidance (no dosage).
- Localized variants:
  - `dosage_text_i18n`: map of language → dosage sentence.
  - `timing_text_i18n`: map of language → timing sentence.
- The UI prefers these fields; if missing, it falls back to regex from `search_text`, then to static defaults.

## Generation Flow

1. Deterministic parsing builds `ParsedProduct` (form, serving size, etc.).
2. `AIEnricher` prompt requests `generate.dosage_text` and `generate.timing_text` with rules to keep them concise and non-overlapping.
3. `EnrichmentPipeline` copies AI outputs into `EnrichedProduct`.
4. `IngestService` stores them in `ProductDoc` and creates `*_i18n` maps via `TranslationService`.

## API

- `GET /products/{id}?lang={est|en|ru}` returns localized `dosage_text` and `timing_text` when available.
- Also exposes `dosage_text_i18n` and `timing_text_i18n` for all-languages access.

Example:

```bash
curl -s "http://localhost:4000/products/wc_30177?lang=en" | jq '.name, .dosage_text, .timing_text'
```

Possible output:

```json
"XTEND EAA 40 servings Tropical"
"1 scoop (~12.5 g) mixed with 300–500 ml water"
"Before, during, or after workout"
```

## UI Behavior

- File: `ui/src/ui/pdp.js`
- Logic:
  - Prefer `prod.dosage_text` / `prod.timing_text`.
  - Else use regex helpers `extractDosageFromText` / `extractTimingFromText`.
  - Else use `language.js` fallbacks.

## Maintenance Pointers

- Prompt: `AIEnricher.buildPrompt()` includes `dosage_text`, `timing_text` requirements.
- Wiring: `EnrichmentPipeline.applyAiGenerate()` sets new fields.
- Models: `EnrichedProduct` + `ProductDoc` fields and `*_i18n` maps.
- Translations: `TranslationService` handles `dosageText` and `timingText`.
- API language application: `ProductController` and `SearchController` swap to localized values when `?lang` is provided.

## Operational Notes

- If AI is disabled (`OPENAI_API_KEY` missing), `*_i18n` maps may be absent; UI fallbacks apply automatically.
- To force regeneration during ingest:
  - `x-clear-ai-cache: true`
  - `x-clear-translation-cache: true`
