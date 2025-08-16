# IronDust Dropshipping Search (MVP)

Minimal Spring Boot (WebFlux, Java 21) API that ingests products from the WooCommerce Store API (`https://www.irondust.eu`) into Meilisearch and exposes:

- POST `/ingest/full` — rebuild index from Store API (admin-key protected)
- POST `/search` — keyword search with filters/sort/pagination
- GET `/products/{id}` — hydrate one product by ID (e.g., `wc_31476`)
- GET `/healthz` — health check (API + Meilisearch)

## Quick start (Docker)

Prerequisites:
- Docker Desktop running

Commands:
```bash
# from repo root
docker compose up -d --build

# health
curl http://localhost:4000/healthz

# full ingest
curl -X POST http://localhost:4000/ingest/full -H "x-admin-key: dev_admin_key"

# search
curl -X POST http://localhost:4000/search \
  -H "Content-Type: application/json" \
  -d '{
    "q": "citrulline",
    "filters": {"in_stock": true},
    "sort": ["price_cents:asc"],
    "page": 1,
    "size": 5
  }'

# product hydrate (example ID from search)
curl http://localhost:4000/products/wc_31476
```

## Configuration

Configured via `src/main/resources/application.yml` and environment variables.

- App
  - `app.adminKey` (default: `dev_admin_key`) — header `x-admin-key` for `/ingest/full`
  - `app.baseUrl` (default: `https://www.irondust.eu`) — Woo Store API base
  - `app.perPage` (default: `100`) — pagination size for Store API
  - `app.indexName` (default: `products_lex`)
- Meilisearch
  - `meili.host` (default: `http://127.0.0.1:7700`, in Docker: `http://meili:7700`)
  - `meili.key` (default: `local_dev_key`) — sent as `Authorization: Bearer <key>`

Docker Compose sets `MEILI_HOST`/`MEILI_KEY` for the API container.

## Running without Docker

Requires Java 21. If you have Maven installed locally:
```bash
mvn -DskipTests package
java -jar target/dropshipping-search-0.1.0.jar
```

Then run the same curl commands as above. Ensure Meilisearch is running at `http://127.0.0.1:7700` or override via env:
```bash
MEILI_HOST=http://127.0.0.1:7700 MEILI_KEY=local_dev_key java -jar target/dropshipping-search-0.1.0.jar
```

## API shapes

- POST `/search` request body:
```json
{
  "q": "citrulline 500g",
  "filters": {
    "in_stock": true,
    "brand_slug": "mst-nutrition",
    "categories_slugs": ["l-sitrulliin"]
  },
  "sort": ["price_cents:asc"],
  "page": 1,
  "size": 24
}
```
- Response excerpt:
```json
{
  "items": [ /* product docs */ ],
  "total": 123,
  "facets": {
    "brand_slug": { "mst-nutrition": 12 },
    "categories_slugs": { "l-sitrulliin": 21 }
  }
}
```

## Notes
- Index: `products_lex` (primary key `id`)
- Dynamic facets: all discovered `attr_pa_*` from Woo taxonomies
- Settings applied at ingest: `searchableAttributes`, `filterableAttributes`, `sortableAttributes`
- Ingest uploads in chunks (≤ 500 docs)

## Postman
A ready-to-use collection and environment are in `postman/`.

- Import `postman/irondust-search.postman_collection.json`
- Import `postman/irondust-local.postman_environment.json`
- Select the `IronDust Local` environment and run requests
