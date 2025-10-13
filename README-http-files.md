# HTTP Request Files

This directory contains `.http` files that replace the Postman collection for testing the IronDust Search API. These files can be used with any HTTP client that supports the `.http` format, such as:

- **IntelliJ IDEA / WebStorm** (built-in HTTP client)
- **VS Code** (with REST Client extension)
- **JetBrains HTTP Client** (standalone tool)

## Files

### `api-requests.http`
Contains all public API endpoints:
- Health checks
- Search endpoints (basic, hybrid, AI)
- Product endpoints (get, alternatives, complements)
- Ingest endpoints
- Vector management
- Content endpoints
- Feature flags

### `admin-requests.http`
Contains all admin-only endpoints:
- Raw data inspection
- Blacklist management
- Admin feature flags
- Ingest & index management
- Admin UI access

## Usage

1. **Set Variables**: Update the variables at the top of each file:
   ```http
   @baseUrl = http://localhost:4000
   @adminKey = dev_admin_key
   @productId = wc_31476
   ```

2. **Run Requests**: Click the "Run" button next to any request in your IDE, or use the keyboard shortcut (usually `Ctrl+Enter` or `Cmd+Enter`)

3. **View Responses**: Responses will appear in a new tab or panel in your IDE

## Environment Variables

The files use these default variables:
- `@baseUrl`: API base URL (default: `http://localhost:4000`)
- `@adminKey`: Admin authentication key (default: `dev_admin_key`)
- `@adminBasic`: Base64 encoded admin credentials (default: `YWRtaW46YWRtaW4=`)
- `@productId`: Sample product ID (default: `wc_31476`)
- `@wooId`: Sample WooCommerce ID (default: `31476`)

## Authentication

- **Public endpoints**: No authentication required
- **Admin endpoints**: Require `x-admin-key: dev_admin_key` header
- **Admin UI**: Requires HTTP Basic Auth (`admin:admin`)

## Examples

### Quick Search Test
```http
POST {{baseUrl}}/search
Content-Type: application/json

{
  "q": "protein",
  "filters": { "in_stock": true },
  "page": 1,
  "size": 5
}
```

### Ingest Specific Products
```http
POST {{baseUrl}}/ingest/products
Content-Type: application/json
x-admin-key: {{adminKey}}

{
  "ids": [31476, 31477]
}
```

### Toggle Feature Flag
```http
POST {{baseUrl}}/feature-flags/ai_search?enabled=true
x-admin-key: {{adminKey}}
```

## Migration from Postman

These `.http` files contain all the requests from the original Postman collection (`postman/irondust-search.postman_collection.json`) plus additional endpoints from the API documentation. The Postman collection can be deprecated in favor of these files.

## OpenAPI (auto-generated)

- JSON: `GET http://localhost:4000/v3/api-docs`
- YAML: `GET http://localhost:4000/v3/api-docs.yaml`
- Swagger UI: `http://localhost:4000/swagger-ui.html`

Admin-secured endpoints require header:

```
x-admin-key: dev_admin_key
```

## Benefits

- **Version Control**: `.http` files are text-based and can be easily versioned
- **IDE Integration**: Native support in JetBrains IDEs and VS Code
- **No External Tools**: No need for Postman or other external applications
- **Easy Editing**: Simple text format for quick modifications
- **Team Sharing**: Easy to share and collaborate on via Git
