Here’s a ready-to-drop architecture.md you can paste into Cursor.

⸻

IronDust Dropshipping Search — Architecture

Goal: Ship a fast, “Rufus-style” product search UI for irondust.eu using only the public WooCommerce Store API for now, with a clean upgrade path to enrichment and semantic search later.

Base URL (WooCommerce Store API): https://www.irondust.eu

⸻

1) Scope

MVP (this build):
	•	Products-only search (no guides/goals UI yet).
	•	Server pulls WooCommerce Store API, transforms, and indexes into Meilisearch.
	•	Minimal backend (Spring Boot latest, Java 21) exposing:
	•	POST /ingest/full — rebuilds the index by fetching the Store API
	•	POST /search — text + filters + sort
	•	GET /products/{id} — get one indexed document
	•	GET /healthz — health check

Not included now (see “Out of scope” below):
	•	Webhooks, REST auth, private fields, orders API, enrichment AI, vector DB.

⸻

2) Data sources
	•	Store API (read-only, no auth):
	•	GET /wp-json/wc/store/v1/products?per_page=100&page=N
	•	GET /wp-json/wc/store/v1/products/categories
	•	GET /wp-json/wc/store/v1/products/attributes
	•	GET /wp-json/wc/store/v1/products/attributes/{attributeId}/terms

We preserve real Woo taxonomies (e.g., pa_tootja, pa_flavor, pa_size).

⸻

3) Indexes

3.1 products_lex (Meilisearch) — IN SCOPE

Purpose: Fast keyword search, facets, and sorting.

Primary key: id (string, e.g. wc_50674)

Document schema (flattened, facet-friendly):

{
  "id": "wc_<productId>",
  "parent_id": null,                         // reserved for future variation grouping
  "type": "simple|variable|bundle|grouped",
  "sku": "string|null",
  "slug": "string",
  "name": "string",
  "permalink": "https://www.irondust.eu/...",

  "price_cents": 2990,                      // from prices.price (int)
  "currency": "EUR",
  "in_stock": true,
  "low_stock_remaining": 1,                 // or null
  "rating": 0.0,
  "review_count": 0,
  "images": ["https://..."],

  "categories_ids":   [4199, 5336],
  "categories_slugs": ["l-sitrulliin", "uksikud-aminohapped"],
  "categories_names": ["L-Sitrulliin", "üksikud aminohapped"],

  "brand_slug": "mst-nutrition",            // from pa_tootja (first term)
  "brand_name": "MST Nutrition®",

  "attr_pa_tootja": ["mst-nutrition"],      // dynamic: one field per taxonomy pa_*
  // e.g. attr_pa_flavor, attr_pa_size, ...

  "search_text": "name + stripped(description) + categories + brand"
}

Meilisearch settings (initial):

{
  "searchableAttributes": ["name", "search_text", "sku"],
  "filterableAttributes": [
    "in_stock", "categories_slugs", "categories_ids", "brand_slug", "price_cents"
    // + every discovered dynamic "attr_pa_*" at ingest time
  ],
  "sortableAttributes": ["price_cents", "rating", "review_count", "in_stock"]
}

Filter grammar (server builds the string):
	•	Booleans/numbers: in_stock = true / price_cents >= 1000
	•	Strings: brand_slug = 'mst-nutrition'
	•	Arrays become OR groups: (categories_slugs = 'l-sitrulliin' OR categories_slugs = 'protein')

Notes:
	•	Keep taxonomies exactly as store uses them (no renaming).
	•	Add distinctAttribute: parent_id later when we group variations.

⸻

3.2 products_vec (Vector DB) — OUT OF SCOPE NOW (PHASE LATER)

Purpose: Natural-language queries, “alternatives”/similarity.

Engine: Qdrant or Postgres + pgvector
Payload: mirrors products_lex filter fields (brand, categories, attr_pa_*, price, stock)

Text to embed: name + stripped(description) + brand + category names + attrs as tokens

⸻

3.3 content_vec (Guides/FAQ) — OUT OF SCOPE NOW

Purpose: Short coach answers (timing creatine, whey vs plant, etc.)

⸻

3.4 goals (chips) — OUT OF SCOPE NOW

Purpose: Curated intent chips (Strength, Cutting, Vegan, Budget…)

⸻

4) Ingest pipeline (server-pull)

Endpoint: POST /ingest/full (protected by x-admin-key)

Steps:
	1.	Paginate Store API products (100 per page) until empty.
	2.	Transform each product to products_lex doc:
	•	price_cents = int(prices.price)
	•	Extract brand from attributes[].taxonomy == 'pa_tootja'
	•	For every pa_* taxonomy, create attr_pa_*: [term.slug]
	•	search_text = name + stripped(description) + category names + brand_name
	3.	Discover all dynamic attr_pa_* field names across the batch.
	4.	Ensure Meili index exists; apply settings (adding discovered facets).
	5.	Upload documents in chunks (≤500).

Delta strategy (for now):
	•	Call /ingest/full to rebuild everything (827 docs — fast).
	•	Blue/green swap can be added later (optional).

⸻

5) Search API

Endpoint: POST /search
Body:

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

Response:

{
  "items": [ /* product docs */ ],
  "total": 123,
  "facets": {
    "brand_slug": { "mst-nutrition": 12, "...": 34 },
    "categories_slugs": { "l-sitrulliin": 21, "...": 50 }
  }
}

Facet set (initial): brand_slug, categories_slugs (+ top-coverage attr_pa_* later).

⸻

6) Product hydrate API

Endpoint: GET /products/{id}
Returns: the single products_lex document by ID (e.g., wc_50674).

⸻

7) Health

Endpoint: GET /healthz
Checks app + Meili availability.

⸻

8) Local development

Meilisearch (Docker):

# docker-compose.yml
services:
  meili:
    image: getmeili/meilisearch:v1.7
    environment:
      MEILI_MASTER_KEY: local_dev_key
    ports: ["7700:7700"]
    volumes: ["./meili_data:/meili_data"]

App config (Spring Boot):

server:
  port: 4000

app:
  adminKey: dev_admin_key
  baseUrl: https://www.irondust.eu
  perPage: 100
  indexName: products_lex

meili:
  host: http://127.0.0.1:7700
  key: local_dev_key

Bootstrap sequence:
	1.	docker compose up -d (Meili)
	2.	mvn spring-boot:run (API)
	3.	curl -X POST http://localhost:4000/ingest/full -H "x-admin-key: dev_admin_key"

⸻

9) Security/ops notes
	•	Protect /ingest/full with an admin key and (optionally) IP allowlist.
	•	Rate-limit /search; cache hot queries in memory (~30–60s).
	•	CORS: allow only your frontends.
	•	No secrets stored in code or artifacts.

⸻

10) Out of scope (next phases)

(A) WooCommerce REST + Orders (Phase Next)
	•	Create orders: POST /wp-json/wc/v3/orders (requires consumer key/secret)
	•	Full variation matrices: GET /wc/v3/products/{id}/variations
	•	Webhooks for price/stock deltas

(B) Deterministic enrichment (no AI)
	•	Unit normalization (g/kg/ml/servings)
	•	Computed: price_per_100g, price_per_serving
	•	Parsing form/size/flavor from titles/descriptions
	•	Variation grouping → enable distinctAttribute: parent_id

(C) Light AI enrichment (constrained JSON)
	•	goal_tags, diet_tags, ingredients_key, warnings
	•	benefit_snippet (≤160 chars), faq[]
	•	Multilingual synonyms (EN/RU/EE)
	•	Safety/claims flagging with confidence + provenance

(D) Semantic search & recommendations
	•	products_vec (Qdrant/pgvector) + hybrid (BM25 + vector, RRF)
	•	PDP “Alternatives” via nearest-neighbors with category/price constraints

(E) Coach rail
	•	content_vec (curated guides/FAQ)
	•	goals table (chips with default filters)

(F) Blue/green indexing
	•	Import into products_lex_YYYYMMDDHHmm → flip CURRENT_INDEX pointer

⸻

11) Acceptance criteria (MVP)
	•	Full catalog indexed (≈827 docs); /search p95 < 200ms (warm).
	•	Filters working for stock, brand, category, and price.
	•	Ingest is one button/one call (/ingest/full) and idempotent.
	•	No schema churn required to add enrichment fields later.

⸻

TL;DR: Build products_lex (Meilisearch) now; keep document fields aligned with real Woo taxonomies. Add enrichment, vectors, orders, and coach features in later phases without changing the public API or UI wiring. Base URL is https://www.irondust.eu so you can run this out of the box.

⸻

12) Implementation notes (MVP)
	• Backend is Spring Boot 3.3 (Java 21), reactive (WebFlux).
	• Meilisearch v1.7 in Docker. API authenticates via Authorization: Bearer <key>.
	• Ingest pipeline: server-pull (Store API) → transform to products_lex → discover dynamic attr_pa_* → ensure index + settings → upload in chunks (≤500).
	• Search returns items, total, and facet distributions for brand_slug and categories_slugs. More facets can be added via settings at ingest.
	• Product hydrate fetches the single Meilisearch document by id (e.g., wc_50674).

Local run (Docker):
	1. docker compose up -d --build
	2. curl http://localhost:4000/healthz → {"ok": true}
	3. curl -X POST http://localhost:4000/ingest/full -H "x-admin-key: dev_admin_key"
	4. curl -X POST http://localhost:4000/search -H "Content-Type: application/json" -d '{"q":"citrulline","filters":{"in_stock":true},"sort":["price_cents:asc"],"page":1,"size":5}'
	5. curl http://localhost:4000/products/wc_31476

Operational notes:
	• Meilisearch host/key are configurable via env: MEILI_HOST / MEILI_KEY (compose sets host to http://meili:7700).
	• /ingest/full is protected by x-admin-key (config: app.adminKey). Add network/IP filtering at the gateway if needed.
	• Rate-limiting and caching for /search can be added via Spring filters or a reverse proxy.