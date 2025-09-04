# API Reference

Base URL: http://localhost:4000

## Ingest

- POST /ingest/full — Ingest all products
- POST /ingest/products — Ingest specific products by ID

### Ingest response shape (excerpt)

Full ingest returns a JSON report with totals, per-product details, and ignored items metadata:

```json
{
  "indexed": 1234,
  "warnings_total": 12,
  "conflicts_total": 3,
  "ignored_count": 2,
  "ignored_ids": ["wc_38369", "wc_38370"],
  "products": [
    { "id": "wc_30177", "warnings": [], "conflicts": [] }
  ]
}
```

Ignored items include non-supplement products (e.g., gift cards, vouchers). They are detected by name/slug/description tokens and category names/slugs.

### Ingest history

- On successful completion of a full ingest, the API writes a JSON snapshot of the final report to disk for auditing.
- Location: configurable via `app.ingestHistoryDir` (default: `tmp/ingest-history`).
- Filename format: `ingest_YYYYMMDD_HHmmss±HH:MM.json` (colons replaced by hyphens for filesystem safety).

### Cache management headers

- `x-clear-ai-cache: true` — Clears AI enrichment cache on disk (`tmp/ai-enrichment-cache.json`)
- `x-clear-translation-cache: true` — Clears translation cache on disk (`tmp/translation-cache.json`)

Both headers are optional and can be included with either ingest endpoint. Requires `x-admin-key`.

### Ingestion performance and parallelism

The full ingestion endpoint performs enrichment and indexing with bounded parallelism.

- Transformation parallelism: controlled by `app.ingestParallelism`
- Meilisearch upload concurrency: controlled by `app.meiliConcurrentUpdates`
- Upload chunk size: controlled by `app.uploadChunkSize`

Defaults are defined in `src/main/resources/application.yml`. Tune these based on CPU, network, and Meilisearch capacity.

Example:

```bash
curl -X POST http://localhost:4000/ingest/products \
  -H "Content-Type: application/json" \
  -H "x-admin-key: dev_admin_key" \
  -H "x-clear-ai-cache: true" \
  -H "x-clear-translation-cache: true" \
  -d '{"ids": [31476, 31477]}'
```

## Search

- POST /search — Search products with filters and sorting (now with adaptive hybrid gating)
- POST /search/hybrid — Force Hybrid search (BM25 + vectors via RRF)
- POST /search/ai — AI quick answer, grounded in top results (feature-flagged)

### Variant grouping (flavors)

- Variants that only differ by flavor/size are grouped by `parent_id` (Meili `distinctAttribute`).
- On ingest, we aggregate group metadata so the UI can render a single card with a flavor chooser:
  - `dynamic_attrs.flavors`: union of flavors across the group
  - `price`/`price_cents`: set to the cheapest variant in the group
  - `price_per_serving`/`price_per_100g`: set to group minimums when available
- Alternatives exclude items with the same `parent_id` as the current product.

## Content

- POST /ingest/content/minimal — Admin-only seed of content index
  - Headers: `x-admin-key: <key>`
  - Seeds Wikipedia summaries (Creatine, Whey protein, Electrolyte) and recent FDA supplement recalls
  - Response: `{ "indexed": <number> }`

- POST /content/search — Search editorial/external content
  - Body: `{ "q": string, "page": number, "size": number, "filter"?: string }`
  - Returns Meilisearch response with facets: `source`, `license`, `language`, `topic`, `tags`
  - Each hit includes `source` and `license` for attribution

- POST /content/render — Render on-site HTML for eligible sources
  - Body: `{ "hit": { "id": string, "source": string, "license": string, "title": string, "excerpt": string, "url"?: string, "language"?: string } }`
  - Returns `{ "html": string, "meta": { "source": string, "license": string, "url": string, "title": string, "language": string } }`
  - 451 when the content is not eligible for on-site rendering (e.g., non-commercial or unknown licenses)
  - Supported sources now: Wikipedia (CC BY-SA 4.0 lead text) and FDA (Public Domain)
  - Always shows attribution and license box under the rendered body

Licensing notes:
- Wikipedia: Include attribution, link to the article and CC BY-SA 4.0. Mark modifications if any. The license applies to this block, not the whole page.
- FDA/NIH/CDC: Public domain (U.S. federal). Avoid third-party images/logos.
- NHS (OGL v3.0): Allowed with attribution; do not imply endorsement. (Future)

### UI: Ask AI

- The UI includes an Ask AI button (header and in the search overlay) and an AI Answer card rendered above results when `ai_search` is enabled.
- The AI Answer card shows: a concise answer, cited items, and suggested refinement chips (e.g., sort by price per serving, Vegan).
- The feature flag can be toggled at runtime via `/feature-flags/ai_search` without redeploying.

Example:

```bash
curl -X POST http://localhost:4000/search \
  -H "Content-Type: application/json" \
  -d '{"q": "citrulline", "page": 1, "size": 5}'
```
AI example:

```bash
curl -s -X POST http://localhost:4000/search/ai \
  -H 'Content-Type: application/json' \
  -d '{"q":"cheap protein","filters":{"in_stock":true},"page":1,"size":6}'
```


Hybrid example:

```bash
curl -X POST http://localhost:4000/search/hybrid \
  -H "Content-Type: application/json" \
  -d '{"q": "vegan preworkout", "filters": {"in_stock": true}, "page": 1, "size": 6}'
```

Latency notes:

- The hybrid endpoint runs Meilisearch and Qdrant in parallel with a short vector-side timeout.
- If the vector call exceeds `vector.vectorTimeoutMs` (default 150 ms) or the query is shorter than `vector.minQueryLength` (default 3), it falls back to Meili-only results automatically.
- Tune via `vector.vectorTimeoutMs`, `vector.minQueryLength`, and `vector.vectorSearchK` in `application.yml`.

Adaptive hybrid gating in /search:
### AI Answer

- Endpoint: POST /search/ai
- Body: same shape as /search. Returns a short grounded answer plus cited items.
- Feature flag: `ai_search` (default enabled). Toggle at runtime:

Enable:
```bash
curl -X POST 'http://localhost:4000/feature-flags/ai_search?enabled=true'
```

Disable:
```bash
curl -X POST 'http://localhost:4000/feature-flags/ai_search?enabled=false'
```

Read:
```bash
curl 'http://localhost:4000/feature-flags/ai_search?defaultValue=true'
```

Notes:
- Current implementation returns a concise heuristic, grounded in Meilisearch top‑K; can be upgraded to LLM later.

UI behavior:
- Normal results remain visible and interactive. AI renders above them and does not block.
- Clear loading and graceful fallback states are shown.
- Answers are cached for the session and localized by `lang`.

- /search defaults to lexical (Meilisearch) for instant responses on head terms.
- It triggers hybrid only when beneficial:
  - Low recall: total results < max(24, size)
  - Semantic-looking query: 4+ tokens or contains “similar/like/alternative/instead of”
  - Cross‑locale hints: Cyrillic characters or Estonian diacritics in the query
- A pre-trigger will call hybrid first if the query looks semantic/cross‑locale; otherwise we run lexical first and fall back to hybrid only when recall is low.
- Vector side budget is bounded by `vector.vectorTimeoutMs` and `vector.minQueryLength`.

### Filterable and sortable fields

Core facets:

- `in_stock`, `categories_slugs`, `categories_ids`, `brand_slug`, `form`, `diet_tags`, `goal_tags`, `parent_id`, `is_on_sale`

Price metrics (filterable and sortable):

- `price` (euros)
- `price_per_serving`
- `price_per_serving_min`, `price_per_serving_max` (when servings are a range)
- `price_per_100g`
- `price_per_unit`

Examples:

```json
{
  "q": "",
  "page": 1,
  "size": 6,
  "filters": {
    "in_stock": true,
    "price_per_serving": { "op": "<=", "value": 2 }
  }
}
```

When `price_per_serving` is null for items with serving ranges, use the range-bound fields:

```json
{ "filters": { "price_per_serving_max": { "op": "<=", "value": 2 } } }
```

## Products

- GET /products/{id} — Get specific product details
- GET /products/{id}/alternatives — Recommended alternatives for a product
 - GET /products/{id}/complements — Complementary items (often bought together)

### Alternatives

Returns products similar to the target product. Backed by Qdrant recommend API and enriched with Meilisearch documents. Always filters `in_stock = true`, and excludes the same product and products from the same variation group (`parent_id`). Respects `?lang` where applicable for localized fields.

Request:

```bash
curl -s "http://localhost:4000/products/wc_30177/alternatives?limit=8&lang=en" | jq '.items | length'
```

Response shape (excerpt):

```json
{
  "items": [ { "id": "wc_123", "name": "...", "price_cents": 1990 } ],
  "total": 8
}
```

### Complements

Returns items that pair well with the target product but are not direct substitutes. Always in-stock and excludes the same product and variation siblings. Heuristics prefer different form/category and boost items sharing goal tags. Sorted by rating and review count.

Request:

```bash
curl -s "http://localhost:4000/products/wc_30177/complements?limit=8&lang=en" | jq '.items | length'
```

Response shape is identical to Alternatives.

### Fields (excerpt)

- `dosage_text` — AI-extracted single-sentence dosage. Localized when `?lang` is provided.
- `timing_text` — AI-extracted single-sentence timing. Localized when `?lang` is provided.
- `dosage_text_i18n` — Map of language → dosage sentence.
- `timing_text_i18n` — Map of language → timing sentence.

Language overrides: when `?lang` is set (`est`, `en`, `ru`), the API returns localized values from the corresponding `*_i18n` entries when available.

Example:

```bash
curl -s "http://localhost:4000/products/wc_30177?lang=en" | jq '.name, .dosage_text, .timing_text'
```

Expected output (shape):

```json
"XTEND EAA 40 servings Tropical"
"1 scoop (~12.5 g) mixed with 300–500 ml water"
"Before, during, or after workout"
```

## Health

- GET /health — Health check
- GET /health/meili — Meilisearch health check
- GET /health/qdrant — Qdrant health check (future)

## Vectors (Admin)

- POST /vectors/reindex/all — Build or refresh all product vectors in Qdrant
- POST /vectors/reindex — Build or refresh vectors for specific product IDs

Both require header `x-admin-key`. Defaults use `text-embedding-3-large` (3072) and `products_vec_lg`.

Examples:

```bash
curl -X POST http://localhost:4000/vectors/reindex/all \
  -H "x-admin-key: dev_admin_key"

curl -X POST http://localhost:4000/vectors/reindex \
  -H "Content-Type: application/json" \
  -H "x-admin-key: dev_admin_key" \
  -d '{"ids": ["wc_31476","wc_31477"]}'
```

## Admin UI (basic)

- Base URL: `http://localhost:4000/admin-ui` (same Spring app)
- Auth: HTTP Basic using `app.adminUsername` / `app.adminPassword` (defaults `admin` / `admin`)

### Admin endpoints

- POST `/admin/ingest/reingest` → starts full reingest. Response: `{ runId, type: "ingest", status }`
- POST `/admin/index/reindex?batchSize=100` → starts full reindex. Response: `{ runId, type: "index", status }`
- GET `/admin/runs/latest?type=ingest|index` → latest run summary
- GET `/admin/runs/{runId}` → run details
- GET `/admin/runs/{runId}/logs/stream` → SSE stream of logs for the run

### Feature flags (admin)

- GET `/admin/feature-flags` → `{ key: boolean, ... }`
- PATCH `/admin/feature-flags/{key}` body: `{ "enabled": true|false }` → returns updated flags

Notes:
- Feature flags are persisted to a JSON file configured by `app.featureFlagsPath` (default `tmp/feature-flags.json`).
- SSE logs are emitted by the admin service. Native SSE from ingest/reindex can be proxied later.
