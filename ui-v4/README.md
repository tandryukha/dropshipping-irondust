# ui-v4 (React + Vite)

## Quickstart

### Start the API and services (recommended)

From repo root:

```bash
cd /Users/tandryukha/dropshipping-irondust
./rebuild-and-watch.sh
```

This starts the Spring Boot API at http://localhost:4000 (Meilisearch 7700, Qdrant 6333). Verify:

```bash
curl :4000/healthz
```

### Start ui-v4 in dev (HMR)

```bash
cd /Users/tandryukha/dropshipping-irondust/ui-v4
npm install
npm run dev -- --host
```

Open http://localhost:5173

### Build and preview (production-like)

```bash
cd /Users/tandryukha/dropshipping-irondust/ui-v4
npm run build
npm run preview -- --host
```

Open http://localhost:4173

## Notes

- ui-v4 calls the API at http://localhost:4000. Ensure the API is running for search/basket to work.
- If you want to compare visuals with the mock (ui-v3):

```bash
cd /Users/tandryukha/dropshipping-irondust/ui-v3
./serve.sh
```

Open http://localhost:8011
