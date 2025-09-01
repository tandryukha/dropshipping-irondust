# How to Run IronDust Dropshipping Search

This guide explains how to run the IronDust Dropshipping Search system locally.

## Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)
- Maven (for local development)

## Quick Start

### 1. Clone and Setup

### 2. Start Services

The easiest way to start all services is using the provided script:

```bash
./rebuild-and-watch.sh
```

### What `rebuild-and-watch.sh` Does

The `rebuild-and-watch.sh` script:

- **Loads environment**: Automatically loads variables from `.env` file if present
- **Rebuilds services**: Stops existing containers, rebuilds the API service, and starts all services
- **Handles AI enrichment**: 
  - Checks for `OPENAI_API_KEY` environment variable
  - If not set, AI enrichment is automatically disabled
  - You can control AI enrichment via `AI_ENRICH` (defaults to `true`)
- **Monitors logs**: Tails logs from all services so you can see what's happening
- **Provides status**: Shows service status and available URLs

### 3. Optional: Configure AI Enrichment

If you want to enable AI enrichment features, create a `.env` file in the repository root:

```bash
# .env
OPENAI_API_KEY=your_openai_key_here
AI_ENRICH=true
```

Then rerun the startup script:

```bash
./rebuild-and-watch.sh
```

## Available Services

Once running, you'll have access to:

- **API**: http://localhost:4000
- **Meilisearch**: http://localhost:7700  
- **Qdrant**: http://localhost:6333 (default collection: `products_vec_lg`)
- **UI** (if running): http://localhost:3000

## Testing the System

### Test Product Ingestion

Ingest specific products to test the enrichment pipeline:

```bash
curl -X POST http://localhost:4000/ingest/products \
  -H "Content-Type: application/json" \
  -H "x-admin-key: dev_admin_key" \
  -d '{"ids": [31476, 31477]}'
```

### Test Search Functionality

Search for products:

```bash
curl -X POST http://localhost:4000/search \
  -H "Content-Type: application/json" \
  -d '{"q": "citrulline", "page": 1, "size": 5}'
```

### Test Hybrid Search

```bash
curl -X POST http://localhost:4000/search/hybrid \
  -H "Content-Type: application/json" \
  -d '{"q": "vegan protein", "filters": {"in_stock": true}, "page": 1, "size": 6}'
```

### Build or Refresh Vector Index (defaults: text-embedding-3-large, 3072-dim, products_vec_lg)

Build all vectors (admin-only):

```bash
curl -X POST http://localhost:4000/vectors/reindex/all \
  -H "x-admin-key: dev_admin_key"
```

Refresh specific products:

```bash
curl -X POST http://localhost:4000/vectors/reindex \
  -H "Content-Type: application/json" \
  -H "x-admin-key: dev_admin_key" \
  -d '{"ids": ["wc_31476","wc_31477"]}'
```

### Check Indexed Data

View enriched product data in Meilisearch:

```bash
curl -X GET "http://localhost:7700/indexes/products_lex/documents/wc_31476" \
  -H "Authorization: Bearer local_dev_key"
```

## Monitoring and Debugging

### View Enrichment Logs

Monitor the enrichment pipeline:

```bash
docker-compose logs api | grep -i "enrich\|pipeline"
```

### Check for Warnings

Look for enrichment warnings or conflicts:

```bash
docker-compose logs api | grep -i "warning\|conflict"
```

### Service Status

Check if all services are running properly:

```bash
docker-compose ps
```

Check Qdrant health:

```bash
curl -s http://localhost:6333/healthz | jq
```

## Stopping Services

To stop all services:

```bash
docker-compose down
```

## Manual Startup (Alternative)

If you prefer to start services manually instead of using the script:

```bash
# Start all services
docker-compose up -d

# Follow logs
docker-compose logs -f
```

## Troubleshooting

### AI Enrichment Not Working

- Ensure `OPENAI_API_KEY` is set in your environment or `.env` file
- Check that `AI_ENRICH=true` is set
- Verify the API key is valid and has sufficient credits

### Services Not Starting

- Check if ports 4000 and 7700 are available
- Ensure Docker and Docker Compose are installed and running
- Check Docker logs: `docker-compose logs`

### Enrichment Pipeline Issues

- Monitor logs for specific error messages
- Check that Meilisearch is accessible at http://localhost:7700
- Verify the WooCommerce store is accessible

## Development

For local development without Docker:

```bash
# Start Meilisearch
docker-compose up -d meilisearch

# Run Spring Boot application
mvn spring-boot:run
```

This will start the API on http://localhost:4000 with Meilisearch on http://localhost:7700.

## Tuning ingestion performance

The full ingest endpoint supports bounded parallelism:

- `app.ingestParallelism`: number of concurrent product transformations (default 4)
- `app.meiliConcurrentUpdates`: concurrent Meilisearch upload requests (default 3)
- `app.uploadChunkSize`: number of docs per upload chunk (default 500)

You can override these via environment variables or a custom Spring profile. Example using environment variables:

```bash
APP_INGESTPARALLELISM=6 \
APP_MEILICONCURRENTUPDATES=4 \
APP_UPLOADCHUNKSIZE=750 \
mvn spring-boot:run
```

Or edit `src/main/resources/application.yml` and restart the API.
