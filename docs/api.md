# API Reference

Base URL: http://localhost:4000

## Ingest

- POST /ingest/full — Ingest all products
- POST /ingest/products — Ingest specific products by ID

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

## Health

- GET /health — Health check
- GET /health/meili — Meilisearch health check
