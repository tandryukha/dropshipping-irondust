# API Reference

Base URL: http://localhost:4000

## Ingest

- POST /ingest/full — Ingest all products
- POST /ingest/products — Ingest specific products by ID

Example:

```bash
curl -X POST http://localhost:4000/ingest/products \
  -H "Content-Type: application/json" \
  -H "x-admin-key: dev_admin_key" \
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
