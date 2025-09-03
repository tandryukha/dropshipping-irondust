Feature Flags

Runtime-configurable feature toggles managed by the backend. Values are stored in Postgres (`feature_flags` table) and can be changed via HTTP API using the admin key.

Flag table

| Name | Default | Description | How to set (admin) | How to read |
|---|---|---|---|---|
| ai_search | true | Enables AI Answer endpoint `/search/ai` and shows Ask AI in UI. | `POST /feature-flags/ai_search?enabled=true|false` with header `x-admin-key: <admin>` | `GET /feature-flags/ai_search?defaultValue=true` |

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

