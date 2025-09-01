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

- POST /search — Search products with filters and sorting

Example:

```bash
curl -X POST http://localhost:4000/search \
  -H "Content-Type: application/json" \
  -d '{"q": "citrulline", "page": 1, "size": 5}'
```

## Products

- GET /products/{id} — Get specific product details

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
