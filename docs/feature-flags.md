Feature Flags

Runtime-configurable feature toggles managed by the backend. Values are stored in Postgres (`feature_flags` table) and can be changed via HTTP API using the admin key.

Defaults

- On first run, the table is auto-created, and default flags are seeded:
  - `ai_search = true`
  - `normalize_titles = false`

Flag table

| Name | Default | Description | How to set (admin) | How to read |
|---|---|---|---|---|
| ai_search | true | Enables AI Answer endpoint `/search/ai` and shows Ask AI in UI. | `POST /feature-flags/ai_search?enabled=true|false` with header `x-admin-key: <admin>` | `GET /feature-flags/ai_search?defaultValue=true` |
| normalize_titles | false | Populates `display_title` (brand at end) and adds it to search. | `POST /feature-flags/normalize_titles?enabled=true|false` with header `x-admin-key: <admin>` | `GET /feature-flags/normalize_titles?defaultValue=false` |

How to toggle AI off

```bash
# Disable AI (requires admin key)
curl -X POST 'http://localhost:4000/feature-flags/ai_search?enabled=false' \
  -H 'x-admin-key: dev_admin_key'

# Verify
curl -s 'http://localhost:4000/feature-flags/ai_search?defaultValue=true'
```

Re‑enable later

```bash
curl -X POST 'http://localhost:4000/feature-flags/ai_search?enabled=true' \
  -H 'x-admin-key: dev_admin_key'
```

Notes

- All set operations require `x-admin-key` matching `app.adminKey` in backend config.
- Reads do not require admin key and are safe for clients.
- Flags are evaluated at request time; no restart is required.

Admin UI

- The Admin UI exposes feature flags at `/admin-ui` (Basic Auth). The Admin UI calls:
  - `GET /admin/feature-flags` to list
  - `PATCH /admin/feature-flags/{key}` with JSON body `{ "enabled": true|false }` and header `x-admin-key` to update

