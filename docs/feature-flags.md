Feature Flags

Runtime-configurable feature toggles managed by the backend. Values are stored in Postgres (feature_flags table) and can be changed via API using the admin key.

Current Flags

| Name | Default | Description | Set (admin) | Read |
|---|---|---|---|---|
| ai_search | true | Enables AI answer endpoint `/search/ai`. | `POST /feature-flags/ai_search?enabled=true` with header `x-admin-key: <admin>` | `GET /feature-flags/ai_search?defaultValue=true` |

Notes:
- All set operations require header `x-admin-key` matching `app.adminKey`.
- Reads do not require admin key.

Examples

Enable AI:
```bash
curl -X POST 'http://localhost:4000/feature-flags/ai_search?enabled=true' \
  -H 'x-admin-key: dev_admin_key'
```

Disable AI:
```bash
curl -X POST 'http://localhost:4000/feature-flags/ai_search?enabled=false' \
  -H 'x-admin-key: dev_admin_key'
```

List all:
```bash
curl -s 'http://localhost:4000/feature-flags'
```

Read single:
```bash
curl -s 'http://localhost:4000/feature-flags/ai_search?defaultValue=true'
```

