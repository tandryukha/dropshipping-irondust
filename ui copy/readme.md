## UI Prototype (ES Modules)

**Purpose:** a no-framework, ES modules refactor of the single-file prototype to make features easy to extend while keeping the same UI:
- **Enhanced Search Panel** (Products • Goals • Content)  
- **PDP Copilot** (Q&A chips, alternatives, bundle) + flavor selection
- **Command Palette** (⚡ + ⌘/Ctrl+K)
- **Tiny hash router** for product routes `#/p/:id`

This version separates JS into small modules and supports optional API calls for search and product detail.

### Requirements
- Serve via a static web server (ES modules are blocked on `file://`).
  - Python:
    - `cd ui-prototype && python3 -m http.server 8000`
    - Open `http://localhost:8000`
  - Node:
    - `npx serve -l 8000 ui-prototype`

### Run with optional backend
- Default API base: `http://localhost:4000`
- Override via query param: `http://localhost:8000/?api=http://127.0.0.1:4000`
- Configure in `src/config.js` (export `API_BASE`).

### File layout
```
ui-prototype/
  index.html             # markup + styles
  src/
    main.js              # app entry (mounts header, router, UI islands)
    config.js            # API_BASE
    core/
      bus.js             # EventTarget bus
      store.js           # tiny shared state
      router.js          # super-tiny hash router
      dom.js             # $, $$ helpers
    api/
      api.js             # searchProducts(q, opts), getProduct(id)
    ui/
      search-panel.js    # panel behavior + live search
      pdp.js             # PDP bindings and data load
      flavor-popover.js  # Add-to-cart popover
```

### Routing
- `#/` home/search
- `#/p/:id` product page; loads product via API and updates PDP UI

### Features preserved
- Search panel open/close, goal/filter chips
- PDP flavor selector + qty; Add opens flavor popover (preselects current flavor)
- Cart counter increments via store and updates header pill
- Tabs, Q&A chips, reference overlay, command palette

### Optional: Vite for DX
```
{ "name": "ui-prototype", "private": true,
  "scripts": { "dev": "vite", "build": "vite build", "preview": "vite preview" },
  "devDependencies": { "vite": "^5.4.0" } }
```
`vite.config.js` root `./`, build to `dist/`. Run: `npm i && npm run dev`.

### Editing guide
- API base: edit `src/config.js` or pass `?api=...`
- Search result card rendering: `src/ui/search-panel.js`
- PDP rendering and flavor extraction: `src/ui/pdp.js`
- Popover behavior: `src/ui/flavor-popover.js`

### Definition of done
- App serves without errors via a static server
- Routing works (`#/`, `#/p/:id`), PDP updates from API
- Flavor popover opens from search panel and PDP; cart pill increments
- All network I/O isolated in `src/api/api.js`

### License / usage
Internal prototype for design exploration. Not for public deployment as-is.
