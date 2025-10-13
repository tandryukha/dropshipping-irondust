## UI v3 → API integration plan (product list only)

Scope: render real products in `ui-v3/index.html` using API search, wire real filters/sort/paging, and typeahead. PDP remains out of scope.

### What the UI prototype has
- Product grid with cards: title, brand, category, rating + count, price, stock, flavor chips, Add to Basket.
- Filters (desktop panels + mobile modal):
  - Category, Brand, Price (slider + chips), Flavour, Dietary, Item Form, Container Type, Size, Availability.
- Sorting: Featured, Price asc/desc, Rating desc, Newest.
- Search box with typeahead-style suggestions.
- Pagination.

### What the API provides
- Endpoints
  - POST `/search` adaptive; POST `/search/hybrid` force hybrid.
  - GET `/products/{id}` (PDP later), GET `/products/{id}/alternatives|complements` (later).
- Response shape (search): `{ items: ProductDoc[], total: number, facets: Record<string, Record<string, number>> }`.
- Facets available by default: `brand_slug`, `categories_slugs`, `form`, `diet_tags`, `goal_tags`.
- Common fields for cards: `display_title | name`, `brand_name`, `categories_names`, `rating`, `review_count`, `price` (or `price_cents`), `in_stock`, `dynamic_attrs.flavors` (grouped), `images`.
- Filterable fields (excerpt): `in_stock`, `categories_slugs`, `brand_slug`, `form`, `diet_tags`, `goal_tags`, `price`, `price_per_serving`, `price_per_100g`, `price_per_unit`, `is_on_sale`, plus dynamic attrs.
- Sortable fields (excerpt): `price`, `price_per_serving`, `price_per_100g`, `price_per_unit`, `rating`, `review_count`, goal score fields.

### Mismatches and options
- Flavour facet in UI vs API facets: API returns flavors under `dynamic_attrs.flavors` in documents but not as a facet by default.
  - Option A (preferred UI): build Flavor chips client-side from the current result set (union of `dynamic_attrs.flavors`), and filter by issuing a filter on `flavor` or dynamic attr key if available.
  - Option B (API change): add `flavors` to Meili filterable/facet list (derived virtual field) and include it in requested facets from `/search`. This requires minor ingest/index settings change.
- Container Type and Size in UI: not first-class fields in ProductDoc; may exist as dynamic attrs (e.g., `attr_pa_container`, `attr_pa_size`).
  - Option A (preferred initial): derive from `dynamic_attrs` keys when present and filter via IN on those keys.
  - Option B (API change): normalize to canonical fields and expose as filterable facets.
- Category display in UI uses names; API facets are `categories_slugs`. We can display names via `categories_names` on cards, but filter should use `categories_slugs`.
- Sorting "Featured" maps to default relevance; "Newest" isn’t available as a stable sort; omit or map to relevance for now.

### Data contracts and mappings
- Title: prefer `display_title` when present; fallback to `name`.
- Brand: use `brand_name` (fallback `brand_slug`).
- Category label on card: first of `categories_names`.
- Price: prefer `price`; fallback `price_cents / 100`.
- Rating: `rating`; Reviews: `review_count`.
- Stock: `in_stock`.
- Flavors: `dynamic_attrs.flavors` array (already grouped by parent_id during ingest).
- Images: first of `images` (optional in prototype; can keep placeholder initially).

### Filters mapping (UI → API)
- Search text: send `q`.
- In stock toggle: `{ in_stock: true | null }` (default true server-side; set to `null` to include OOS).
- Category chips: set `filters.categories_slugs = [slug, ...]`.
- Brand chips: `filters.brand_slug = [slug, ...]` (UI shows names; map to slug strings).
- Diet chips: `filters.diet_tags = [tag, ...]`.
- Form chips: `filters.form = [form, ...]`.
- Price range slider: numeric range via min/max operators:
  - `filters.price = { op: ">=", value: min }` and `filters.price_per_serving` etc. are optional extra filters.
  - For max bound: `filters.price = { op: "<=", value: max }` sent as a separate conjunct; since API’s builder ANDs all entries, use two distinct keys by suffix, e.g., `price_min` and `price_max` locally and convert to two entries in the request body filter string.
- Quick price chips: translate into the above min/max pair.
- Flavor chips (client-derived): if API keeps dynamic attr only, send `filters.flavor = ["Vanilla", ...]` and/or key `dynamic_attrs.flavors` if we add API-side support. Start with `flavor` equals comparisons; Meili index includes top-level `flavor` where present.
- Container and Size (client-derived from `dynamic_attrs`): send filters under their dynamic keys if present (e.g., `attr_pa_konteiner` or normalized app keys); otherwise omit until normalized.

Note on filter payload shape: the API accepts
- equality with scalars or lists (value or `[...]` → `IN [...]`), and
- comparisons via `{ op, value }`. Multiple entries are ANDed.

### Sort mapping
- Featured: no `sort` field (relevance).
- Price: Low to High → `sort: ["price:asc"]`.
- Price: High to Low → `sort: ["price:desc"]`.
- Avg. Customer Review → `sort: ["rating:desc","review_count:desc"]` (send both; API will pass to Meili; Meili supports multi-sort on sortable fields).
- Newest: not supported; map to Featured for now.

### Pagination
- UI uses `page` (1-based) and page size; send `page` and `size` fields.

### Step-by-step integration (execute one step at a time)
1) Add a tiny fetch layer for UI v3
   - Create a small module `ui-v3/api.js` mirroring `ui-v2/src/api/api.js` with `searchProducts` only.
   - Use `POST /search` for structured sort; use `/search/hybrid` for natural queries without sort (same logic as v2).
   - Include `lang` when available (optional in v3 for now).

2) Replace mock DATA with first real query render
   - On page load, call `searchProducts('', { page:1, size:32, filters:{ in_stock: true } })`.
   - Render returned `items` into the grid, using mappings above. Keep placeholder/images for now.
   - Update the count label from `total`.

3) Wire the search input
   - On Enter or click Go, call `searchProducts(q, { page:1, size:32, filters: assembleFiltersFromState(), sort: assembleSort() })`.
   - Close suggestions.
   - For now, keep the existing suggestion UI but populate from simple client heuristics or call `/search` with `size:0` and derive brands/forms for keyword? Prefer keeping mock suggestions for first pass.

4) Wire sorting
   - Map dropdown to the sort mapping above.
   - Trigger a new search on change.

5) Wire pagination
   - Build pager buttons based on `total` and page size.
   - On click, call `searchProducts(currentQ, { page: n, size, filters, sort })` and scroll to top.

6) Wire Availability toggle
   - When "Include Out of Stock" is active, set `filters.in_stock = null` (or remove key) to include OOS; otherwise `true`.

7) Wire Category/Brand/Form/Dietary filters
   - For each chip click, update local state sets.
   - Convert sets to arrays under the appropriate API filter keys.
   - Requery.
   - For Category, store slug in state; display name from the card or cache.

8) Wire Price slider and chips
   - Maintain local temp min/max.
   - On Apply/Go, add two comparison entries to request body:
     - We must AND them; since the API map keys are unique, send two synthetic entries by using distinct keys like `price_min` and `price_max` and convert to expressions client-side into a single Meili filter string isn’t supported server-side. Therefore, use this approach:
       - Build a single filter string on the client for price range: `price >= X AND price <= Y` and send via `filters: { "__filterString": "price >= X AND price <= Y" }` only if API supports raw strings. Current API does not accept raw string; Alternative:
       - Send two entries with array-comparison notation: the server currently builds per-key only; workaround is to send two distinct fields understood by backend. Suggested API tweak below.

9) Derive Flavour options and filtering (client-side)
   - After each result set, compute a unique set of values from `item.dynamic_attrs?.flavors || []` to render chips.
   - When a flavor chip is selected, add `filters.flavor = [value]`.
   - Requery.

10) Mobile filters modal
   - Reuse the desktop state; when Apply is pressed, run the same query.

11) Basket UI
   - Keep local-only for now.

### API adjustments recommended (nice-to-have)
- Price range ANDing: Current `FilterStringBuilder` ANDs entries but cannot accept two constraints on the same key without key collision at the JSON level.
  - Option: accept `filters: { price: [{ op: ">=", value: X }, { op: "<=", value: Y }] }` and teach builder to handle a list of maps for a single field.
  - Or accept `filters: { price_min: { op: ">=", value: X }, price_max: { op: "<=", value: Y } }` and map both to `price` during build.
- Flavors as facet: add a virtual `flavors` field to filterable/facets and include it in the controllers’ `facets` list, so UI can render Flavor counts without client derivation.
- Container/Size: normalize common Woo attributes into canonical fields and expose as filterable facets (optional, later).
- Newest sort: add an indexed `date_added` and mark sortable to support "Newest".

### Minimal client helpers (pseudo-code)
```js
// Assemble filter map for API
function assembleFiltersFromState(s){
  const f = {};
  if (s.inStockOnly) f.in_stock = true; // unset to include OOS
  if (s.categories.size) f.categories_slugs = Array.from(s.categories);
  if (s.brands.size) f.brand_slug = Array.from(s.brands);
  if (s.forms.size) f.form = Array.from(s.forms);
  if (s.diets.size) f.diet_tags = Array.from(s.diets);
  if (s.flavors.size) f.flavor = Array.from(s.flavors);
  if (s.priceMin != null) f.price_min = { op: ">=", value: s.priceMin };
  if (s.priceMax != null) f.price_max = { op: "<=", value: s.priceMax };
  return f;
}

function assembleSort(val){
  if (val === 'price-asc') return ["price:asc"]; 
  if (val === 'price-desc') return ["price:desc"]; 
  if (val === 'rating-desc') return ["rating:desc","review_count:desc"]; 
  return undefined; // featured
}
```

If we adopt the API tweak for `price_min/price_max`, the backend should translate those keys to a proper Meili filter string for `price`.

### Test/validation checkpoints (what to validate after each step)
- Step 2: Products render, count matches `total` for trivial query.
- Step 3: Search input modifies results; pagination updates.
- Step 4: Sort applies; verify using extreme values.
- Step 5: Pagination stable across filters; page bounds respected.
- Step 6–9: Each filter updates query and results; inspect network payloads for correct filter shapes; ensure default `in_stock` behavior.

### Roll-forward notes
- Keep `ui-v2/src/api/api.js` as reference; reuse `API_BASE` config pattern.
- Don’t change API contracts unless necessary; prefer client derivation for Flavor/Container/Size in first pass.
- Defer images and PDP to next stage.


