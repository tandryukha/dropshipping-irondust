# Indexing Architecture (WooCommerce → Meilisearch + Vector)

**Status:** Phase 1 complete (Parsing + AI Enrichment). Phase 2 (Vector & Hybrid Search) available: vector indexing to Qdrant and a hybrid search endpoint are live. PDP Alternatives use Qdrant recommend with Meili joins.

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
* **`products_vec` (Qdrant/pgvector)**: vectors + metadata for semantic alternatives and recommendations.
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
* `diet_tags[]` (vegan, lactose_free, sugar_free, gluten_free)
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

### 2.4 VectorRecord (products_vec)

* `id` (Qdrant point id is a UUID derived from Meili `id`; payload includes `doc_id` for joining back)
* `embedding` (float[])
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

### 4.3 Ingestion parallelism & OpenAI throttling

Full ingestion runs with bounded parallelism to reduce wall-clock time while protecting upstream/downstream services:

- Transformation/conversion parallelism is controlled by `app.ingestParallelism` and uses a bounded elastic scheduler.
- Meilisearch uploads run in chunks with concurrent requests controlled by `app.meiliConcurrentUpdates` and chunk size `app.uploadChunkSize`.
- The enrichment pipeline is instantiated per product to avoid shared mutable state.
- All OpenAI calls (translations + AIEnricher) share a process-wide limiter honoring approx `OPENAI_RPM` and `OPENAI_TPM` (defaults: 500 RPM, 200k TPM). Adjust these env vars to your account limits.

Tune these values based on CPU cores, network bandwidth, and Meilisearch throughput. Start conservative (e.g., 4/3 concurrency) and increase gradually while monitoring logs and latency.

### 4.2 Deterministic Parsing Details

**Unit normalization**

* Map: `kg→g` (`×1000`), `l→ml` (`×1000`); normalize decimals `, → .`.
* Regex (examples):

  * serving size: `/(
