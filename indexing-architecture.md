# Indexing Architecture (WooCommerce → Meilisearch + Vector)

**Status:** Phase 0 complete (basic Meilisearch). This document describes Phase 1 (Parsing + AI Enrichment, current) and Phase 2 (Vector & Hybrid Search, future). The goal is an accurate, explainable index with deterministic parsing first, an LLM pass *once per record*, and clean separations of concerns.

---

## 0) Objectives & Principles

**Objectives**

* High‑quality PDP “Alternatives” with transparent reasons and parity constraints (category/form/price/diet).
* Accurate, normalized product facts: units, servings, sizes, forms, flavors.
* Enrichment: `price_per_serving`, `price_per_100g`, `goal_tags`, `diet_tags`, `ingredients_key`, `benefit_snippet`, `faq[]`, multilingual synonyms, and safety/claims flags with provenance.
* Hybrid retrieval: BM25 (Meilisearch) + vectors (Qdrant/pgvector) via RRF (Reciprocal Rank Fusion).
* Deterministic-first, then LLM validation/fill, with conflict detection and WARN logs including product IDs.

**Guiding principles**

* **Deterministic > AI** for facts when possible; AI fills gaps + flags conflicts.
* **Single interface, multiple enrichers**: Parsers (deterministic) and one AI enricher (GPT‑5 mini) implement the same interface; they run in a pipeline.
* **Idempotent, rerunnable**: every step keyed by input hash + `enrichment_version`.
* **Explainability**: store `value`, `confidence`, `source`, and optional `evidence` per enriched field.

---

## 1) High‑Level Data Flow

```
WooCommerce → RawProduct (JSON) ──▶ Normalizer ──▶ ParsedProduct
                                     │                 │
                                     └─▶ AIEnricher ──┘
                                                   │
                              ┌─────────────────────┴──────────────────────┐
                              │                                            │
                        Meilisearch Index                           Vector Index
                      (products_enriched)                          (products_vec)
```

### Artifacts

* **`products_enriched` (Meilisearch)**: canonical searchable docs driving storefront & facets.
* **`products_vec` (Qdrant/pgvector)**: vectors + metadata for semantic alternatives.
* **`alternatives` (optional side index)**: precomputed nearest neighbors + reason codes for fast PDP render.
* **`synonyms_multi`**: generated multilingual synonyms registered into Meilisearch synonyms.
* **Logs**: structured WARN logs for conflicts/inconsistencies.

---

## 2) Data Contracts

### 2.1 RawProduct (example fields we already get)

* `id`, `sku`, `slug`, `permalink`, `name`, `search_text`
* `price_cents`, `currency`, `in_stock`, `low_stock_remaining`
* `brand_name`, `brand_slug`, `images[]`
* `categories_ids[]`, `categories_slugs[]`, `categories_names[]`
* Attributes (locale-specific slugs):

  * `attr_pa_valjalaske-vorm` (form), `attr_pa_maitse` (flavor)
  * `attr_pa_portsjonite-arv` (servings), `attr_pa_grammide-arv` (net grams)
  * `attr_pa_tablettide-arv` (tablets count), etc.
  * `attr_pa_kas-see-on-veganisobralik` (vegan yes/no)

### 2.2 ParsedProduct (deterministic outputs)

Fields computed without AI (best‑effort):

* `form` (powder|capsules|tabs|drink|gel|bar)
* `flavor`
* `net_weight_g` (number)
* `servings` (int), `serving_size_g` (float, optional)
* **Derived**: `price` (euros), `price_per_serving`, `price_per_100g`
* `goal_tags[]` (e.g., preworkout, strength, endurance)
* `diet_tags[]` (vegan, lactose\_free, sugar\_free, gluten\_free)
* `ingredients_key[]` (canonical ingredient tokens, if available deterministically)
* `parent_id` (variation grouping id), `variant_group_id` (if available)
* `warnings[]` (machine-generated, see §6)
* `provenance` map: source per field = `attribute|regex|derived`

### 2.3 EnrichedProduct (after AI pass)

Adds/validates:

* Fill missing: `servings`, `serving_size_g`, `form`, `flavor`, `ingredients_key`, `goal_tags`, `diet_tags`
* Generate: `benefit_snippet` (≤160 chars), `faq[]` (Q/A pairs), `synonyms_multi{en,ru,et}`
* `safety_flags[]` (e.g., caffeine content warning, not for pregnancy, allergen mentions) with `confidence` & `evidence`
* `conflicts[]` (field-level: `field`, `det_value`, `ai_value`, `evidence`)
* `ai_notes` (optional short rationale string)

### 2.4 VectorRecord (products\_vec)

* `id`
* `embedding` (float\[])
* `category_top` (for pre-filter), `price_band` (bucketed), `diet_parity` (e.g., `vegan`), `form`
* `brand`, `ingredients_key[]`, `goal_tags[]`
* `popularity` (optional)

---

## 3) Phase 0 — Meilisearch Basic (Completed)

**Scope**

* Index RawProduct → minimal searchable attributes.
* Basic facets: `brand`, `categories`, `in_stock`.

**Meilisearch settings** (baseline)

* `searchableAttributes`: `name`, `brand_name`, `categories_names`, `search_text`
* `filterableAttributes`: `brand_name`, `categories_slugs`, `in_stock`
* `sortableAttributes`: `price_cents`, `rating`, `review_count`
* `distinctAttribute`: *(none yet)*

**Outcome**: Working search; lacks normalized units, computed fields, grouping, and semantic alternatives.

---

## 4) Phase 1 — Parsing + AI Enrichment (Current)

### 4.1 Component Layout

**Interfaces**

* `EnricherStep` (single interface implemented by all parsers and AI):

  * `boolean supports(RawProduct p)`
  * `EnrichmentDelta apply(RawProduct p, ParsedProduct soFar)`
  * `List<Warn> getWarnings()`
* `EnrichmentDelta`: partial field updates + per-field `{value, confidence, source, evidence?}`
* `Warn`: `{productId, code, field?, message, evidence?}`

**Pipeline order**

1. **Normalizer** (locale/slugs → canonical): map forms, flavors, booleans; unify decimals.
2. **UnitParser**: grams/ml/servings from attributes; fallback regex on `search_text`.
3. **ServingCalculator**: compute `servings` if missing (`net_weight_g / serving_size_g`).
4. **PriceCalculator**: compute `price`, `price_per_serving`, `price_per_100g`.
5. **TaxonomyParser**: `goal_tags` from category + keywords; `diet_tags` from attributes/keywords.
6. **IngredientTokenizer**: token list from name/description when explicitly present.
7. **VariationGrouper**: set `parent_id`/`variant_group_id` (slug heuristics + brand + base title).
8. **ConflictDetector**: scan for contradictions (e.g., attribute says capsules but title says powder). Produces `warnings`.
9. **AIEnricher (GPT‑5 mini, single run per product)**:

   * Validate fields, fill `null`s, harmonize edge cases.
   * Generate `benefit_snippet`, `faq[]`, `synonyms_multi`.
   * Safety/claims flags with confidence + evidence.
   * Emit `conflicts[]` if it disagrees with deterministic values.

**Rules vs AI precedence**

* Deterministic values with high confidence (from explicit attributes) **win** unless AI supplies strong contradictory evidence. Conflicts are **not auto‑overwritten**; they are logged and stored as `ai_suggested_*` for operator review (optional).

### 4.2 Deterministic Parsing Details

**Unit normalization**

* Map: `kg→g` (`×1000`), `l→ml` (`×1000`); normalize decimals `, → .`.
* Regex (examples):

  * serving size: `/(\d+(?:[.,]\d+)?)\s*(g|ml)\s*(?:per|\/)?\s*(serving|portsjon)/i`
  * servings: `/(\d+)\s*(servings|annust|portsjonit|порций)/i`
  * net weight: `/(\d+(?:[.,]\d+)?)\s*(kg|g|l|ml)\b/i`

**Computed**

* `price = price_cents/100`
* `price_per_serving = price / servings` (2 decimals)
* `price_per_100g = price / (net_weight_g/100)` for powders

**Parsing form/size/flavor**

* **Form**: attribute map (e.g., `pulber-et → powder`), fallback keywords in `name/search_text`.
* **Flavor**: slug map (e.g., `ei-mingit-maitset → unflavored`).
* **Size**: from attributes; fallback regex in `search_text`.

**Variation grouping → `distinctAttribute: parent_id`**

* Group variants (flavors/sizes) via a normalized base title (strip flavor/size), brand, and category.
* Store `parent_id` on all variants; enable Meilisearch `distinctAttribute = parent_id`.

**Taxonomy**

* `goal_tags`: map categories/phrases to a fixed set: `preworkout, strength, endurance, lean_muscle, recovery, weight_loss, wellness`.
* `diet_tags`: boolean flags from attributes + keyword rules: `vegan|veganii|веган`, `gluteenivaba`, `laktoosivaba`, `sugar[- ]?free|без сахара`.
* `ingredients_key`: canonical tokens when ingredients explicitly mentioned (no inference).

### 4.3 AI Enrichment (GPT‑5 mini)

**Why**: validate mapping, fill long‑tail gaps, generate short UX texts, capture safety/claims, and build multilingual synonyms.

**Contract (JSON response)**

```json
{
  "fill": {
    "form": {"value": "powder", "confidence": 0.92, "evidence": "..."},
    "flavor": {"value": "unflavored", "confidence": 0.99, "evidence": "..."},
    "servings": {"value": 50, "confidence": 0.98, "evidence": "..."},
    "serving_size_g": {"value": 3.0, "confidence": 0.85, "evidence": "..."},
    "ingredients_key": {"value": ["l-citrulline"], "confidence": 0.9, "evidence": "..."},
    "goal_tags": {"value": ["preworkout","endurance","recovery"], "confidence": 0.8, "evidence": "..."},
    "diet_tags": {"value": ["vegan"], "confidence": 0.95, "evidence": "..."}
  },
  "generate": {
    "benefit_snippet": "Boosts blood flow and endurance; pure L‑citrulline.",
    "faq": [
      {"q": "When to take?", "a": "20–30 minutes before training."},
      {"q": "Is it flavored?", "a": "No, unflavored."}
    ],
    "synonyms_multi": {
      "en": ["l-citrulline", "citrulline powder"],
      "ru": ["L-цитруллин", "цитруллин порошок"],
      "et": ["L‑tsitrulliin", "tsitrulliin pulber"]
    }
  },
  "safety_flags": [
    {"flag": "not_medicine", "confidence": 0.99, "evidence": "..."}
  ],
  "conflicts": [
    {"field": "form", "det_value": "capsules", "ai_value": "powder", "evidence": "..."}
  ]
}
```

**Prompting notes**

* Include **only** the `RawProduct` string fields + parsed partials; ask the model to quote phrases as evidence.
* Strict JSON only; set `max_tokens` small; temperature \~0.
* One call per product (no chain-of-LLMs). Use response to update only `null/low-confidence` fields; never silently override explicit attributes.

**Idempotency & cost**

* Compute `ai_input_hash = sha256(compacted_raw + parsed_core)`. Store on the document. Skip if unchanged.
* Store `ai_enrichment_ts` + `enrichment_version` to enable reconciling when prompts evolve.

---

## 5) Phase 2 — Vector & Hybrid Retrieval (Future)

### 5.1 Vector Store

* **Choice**: Qdrant (managed, ANN ready) or pgvector (keeps everything in Postgres). Either works.
* **Schema (`products_vec`)**

  * `id`
  * `embedding` (float\[])
  * `category_top`, `form`, `diet_parity`, `price_band` (e.g., `p0:<€20`, `p1:€20–40`, ...)
  * `goal_tags[]`, `ingredients_key[]`, `brand`, `popularity`

**Embeddings text** (content‑based): join `[name, brand, category_path, ingredients_key, goal_tags, diet_tags, benefit_snippet]`.

### 5.2 Hybrid Search (BM25 + Vector via RRF)

* **Query time**: run Meili (BM25) and vector kNN in parallel. Normalize scores; fuse with RRF (e.g., k=60).
* **Filters**: apply categorical filters consistently to both branches: `category_top`, `form`, `diet_parity`, price range.
* **Synonyms**: inject multilingual synonyms into Meili for recall; they also improve embedding text.

### 5.3 PDP “Alternatives”

1. Pre‑filter by same `category_top`, `in_stock = true`, exclude same `parent_id` and same `id`.
2. Vector nearest neighbors for the product’s own embedding (k=100).
3. **Constraints**: keep vegan parity; maintain price band ±25% (relax if <3 hits); prefer same `form`.
4. **Re‑rank**: `0.6*cosine + 0.2*specOverlap(ingredients_key, goal_tags) + 0.2*priceCloseness` with a small penalty for same brand (to add variety).
5. Render 2–6 items with **reason codes** (e.g., “Similar ingredients • Vegan • Price ±10%”).

*(Optional)* Nightly job produces `alternatives` side index with top‑N ids per product to avoid query‑time vector calls.

---

## 6) Logging, Conflicts & QA

**WARN log format (one per issue)**

```
level=WARN ts=... product_id=wc_31476 code=FIELD_CONFLICT field=form det=powder ai=capsules evidence="..." source=ai_enricher
```

Other codes: `MISSING_CRITICAL(servings)`, `UNIT_AMBIGUITY`, `BAD_VARIATION_GROUP`, `UNSUPPORTED_CLAIM`, `INGREDIENT_PARSE_FAIL`.

**Metrics**

* Coverage of critical fields (`servings`, `net_weight_g`, `form`).
* Conflict rate per field.
* Alternatives availability (% with ≥3 candidates).
* CTR on alternatives, add‑to‑cart rate, PDP dwell.

**Human review**

* Weekly export of conflicts for manual fixes in Woo or enrichment mappers.

---

## 7) Meilisearch Settings (after Phase 1)

* `searchableAttributes`: `name`, `brand_name`, `categories_names`, `search_text`, `ingredients_key`, `synonyms_multi.en|ru|et`
* `filterableAttributes`: `brand_name`, `categories_slugs`, `form`, `in_stock`, `diet_tags`, `goal_tags`, `parent_id`
* `sortableAttributes`: `price`, `price_per_serving`, `price_per_100g`, `popularity`
* `distinctAttribute`: `parent_id` (to group variations)
* **Synonyms**: install from `synonyms_multi` lists (dedup, limit size, prefer curated seed + AI augment).

---

## 8) Operations & Triggers

**When to re‑enrich**

* Price/stock change → recompute derived prices only.
* Text/attributes change → recompute deterministic parsers; if `ai_input_hash` changes, re‑run AI.
* Taxonomy map updates → re‑run affected parsers only (no AI required).

**Batching & rate limiting**

* Process in shards (e.g., 100 products). Respect API rate limits; exponential backoff on AI errors.

**Versioning**

* `enrichment_version` integer; bump when schema/prompt changes; store alongside enriched fields.

---

## 9) Example: Applied to `wc_31476` (Citrulline RAW 250g)

**Deterministic output (illustrative)**

```
form = powder (attr map)
flavor = unflavored (slug map)
net_weight_g = 250 (attribute)
servings = 50 (attribute)
serving_size_g ≈ 3 (regex from search_text)
price = 18.90 EUR
price_per_serving = 0.38 EUR
price_per_100g = 7.56 EUR
goal_tags = [preworkout, endurance, strength]
diet_tags = [vegan]
parent_id = mst-citrulline-raw (heuristic)
```

**AI adds**

* `ingredients_key = ["l-citrulline"]`
* `benefit_snippet` (≤160 chars) and 2–3 `faq` entries
* `synonyms_multi` for EN/RU/EE
* `safety_flags` (e.g., “not a medicine”); likely **no conflicts** here

---

## 10) Open Trade‑offs & Defaults (chosen for now)

* **AI model**: GPT‑5 mini for every product (one pass). Escalation to full GPT‑5 is *not automatic*; we log conflicts for manual review first.
* **Vector store**: Qdrant (default) for speed; pgvector viable if you prefer fewer moving parts.
* **Embedding model**: small sentence embedding model (e.g., MiniLM‑class/BGE‑small). Keep 384–768 dims to save RAM.
* **Price parity window**: ±25% (relaxation steps to ±35% if <3 alts).
* **Diet parity**: strict (vegan only → vegan alternatives only).
* **Same brand**: slight penalty in re‑rank to promote variety.

---

## 11) Implementation Checklist

**Phase 1 (now)**

* [ ] Implement `EnricherStep` interface and pipeline executor.
* [ ] Deterministic parsers (Normalizer, UnitParser, ServingCalculator, PriceCalculator, TaxonomyParser, IngredientTokenizer, VariationGrouper, ConflictDetector).
* [ ] GPT‑5 mini `AIEnricher` with strict JSON schema, evidence quotes, and idempotency hash.
* [ ] Enriched Meilisearch settings + synonyms loader.
* [ ] Structured WARN logging + weekly export.

**Phase 2 (next)**

* [ ] Embedding generator + `products_vec` upsert.
* [ ] Hybrid search service (parallel BM25 + kNN, RRF fusion).
* [ ] PDP Alternatives service with constraints + re‑rank + reason codes.
* [ ] (Optional) Nightly precompute `alternatives` side index.

---

## 12) What *not* to do (Non‑Goals)

* No real‑time per‑user personalization yet (that can be a later layer).
* No heavy AI chains; exactly one AI pass per product.

---

## 13) Appendix — Suggested Field List in Meilisearch `products_enriched`

Identifiers & media:

* `id`, `sku`, `slug`, `permalink`, `images[0]`

Basics:

* `name`, `brand_name`, `categories_names`, `categories_slugs`, `in_stock`, `low_stock_remaining`

Parsed + Derived:

* `form`, `flavor`, `net_weight_g`, `servings`, `serving_size_g`, `price`, `price_per_serving`, `price_per_100g`
* `goal_tags[]`, `diet_tags[]`, `ingredients_key[]`, `parent_id`, `variant_group_id`

Generated:

* `benefit_snippet`, `faq[]`, `synonyms_multi{en,ru,et}`
* `safety_flags[]`

Diagnostics:

* `warnings[]`, `conflicts[]`, `provenance{field→source}`, `confidence{field→score}`
* `enrichment_version`, `ai_input_hash`, `ai_enrichment_ts`

---

**End of document.**
