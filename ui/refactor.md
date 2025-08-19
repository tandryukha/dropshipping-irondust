Here’s a ready-to-paste refactor.md you can drop into your repo and feed to Cursor.

⸻

Refactor: ship-fast micro-structure (with routing)

Goal: keep the current UI and markup, but move JavaScript into small ES modules so features (search, PDP, flavors, checkout) are easy to extend. No framework. Add a tiny hash router.

Why this now: minimum change, maximum leverage. You’ll keep shipping speed while gaining clean seams (api, store, bus, router, and two “islands”: search panel + PDP with flavor popover).

⸻

Target file layout

ui-prototype/
  index.html
  src/
    main.js
    config.js
    core/
      bus.js
      store.js
      router.js
      dom.js
    api/
      api.js
    ui/
      search-panel.js
      pdp.js
      flavor-popover.js
      analytics.js   (optional)

Keep CSS and HTML as-is. We only remove inline <script> from index.html and load src/main.js.

⸻

Step 1 — edit index.html
	•	Remove the big inline <script>…</script> block at the end.
	•	Add a module entry:

<!-- at the end of <body> -->
<script type="module" src="/src/main.js"></script>

(Everything else in index.html stays unchanged — all IDs/classes are reused by the modules.)

⸻

Step 2 — core utilities

src/config.js

export const API_BASE =
  new URLSearchParams(location.search).get('api') || 'http://localhost:4000';

src/core/bus.js

// Simple app-wide event bus
export const bus = new EventTarget();
// usage: bus.dispatchEvent(new CustomEvent('event', { detail: {...} }))

src/core/store.js

// Minimal shared state with a notify() helper
import { bus } from './bus.js';

export const store = {
  cartCount: 2,
  pdpQty: 1,
  locale: 'EN',
  set(key, val) { this[key] = val; bus.dispatchEvent(new CustomEvent('store:'+key, { detail: val })); },
  get(key) { return this[key]; }
};

src/core/router.js (hash-router, routing is on)

// Super-tiny hash router with params
const routes = [];
export function route(pattern, handler) {
  // pattern examples: '/', '/p/:id'
  const re = new RegExp('^' + pattern.replace(/:[^/]+/g, '([^/]+)') + '$');
  routes.push([re, handler]);
}
export function navigate(path) {
  if (location.hash.slice(1) === path) return window.dispatchEvent(new HashChangeEvent('hashchange'));
  location.hash = '#'+path;
}
export function start() {
  const go = () => {
    const h = location.hash.slice(1) || '/';
    for (const [re, fn] of routes) {
      const m = h.match(re);
      if (m) return fn(...m.slice(1));
    }
  };
  addEventListener('hashchange', go);
  go();
}

src/core/dom.js

export const $  = sel => document.querySelector(sel);
export const $$ = sel => Array.from(document.querySelectorAll(sel));


⸻

Step 3 — API wrapper

src/api/api.js

import { API_BASE } from '../config.js';

export async function searchProducts(q, { page=1, size=6, filters={}, sort=[] } = {}) {
  const res = await fetch(API_BASE + '/search', {
    method: 'POST',
    headers: { 'Content-Type':'application/json' },
    body: JSON.stringify({ q, page, size, filters, sort })
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // { items: [...] }
}

export async function getProduct(id) {
  const res = await fetch(API_BASE + '/products/' + encodeURIComponent(id));
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json();
}


⸻

Step 4 — UI islands

src/ui/flavor-popover.js

import { $, $$ } from '../core/dom.js';
import { store } from '../core/store.js';

let pop, flavorWrap, confirmAdd, flavorSubtitle, qtyMinus, qtyPlus, qtyVal, title, btnClose;
let currentFlavor = null, currentQty = 1;

export function mountFlavorPopover() {
  pop = $('#flavorPop');
  flavorWrap = $('#flavorChips');
  confirmAdd = $('#confirmAdd');
  flavorSubtitle = $('#flavorSubtitle');
  qtyMinus = $('#qtyMinus');
  qtyPlus = $('#qtyPlus');
  qtyVal = $('#qtyVal');
  title = $('#flavorTitle');
  btnClose = $('#popClose');

  btnClose?.addEventListener('click', close);
  document.addEventListener('keydown', (e)=>{ if(e.key==='Escape' && pop.classList.contains('visible')) close(); });

  qtyMinus?.addEventListener('click', ()=>{ setQty(Math.max(1, currentQty-1)); });
  qtyPlus?.addEventListener('click', ()=>{ setQty(currentQty+1); });

  confirmAdd?.addEventListener('click', ()=>{
    store.set('cartCount', store.get('cartCount') + currentQty);
    $('#cartCount').textContent = String(store.get('cartCount'));
    close();
  });
}

export function openFor(button, { initialFlavor = null, initialQty = 1 } = {}) {
  const r = button.getBoundingClientRect();
  pop.style.left = (r.left + window.scrollX - 40) + 'px';
  pop.style.top  = (r.top + window.scrollY - 10 - pop.offsetHeight/2) + 'px';

  title.textContent = 'Add: ' + (button.dataset.name || 'Product');
  const list = JSON.parse(button.dataset.flavors || '[]');
  flavorWrap.innerHTML = '';
  currentFlavor = null;
  confirmAdd.disabled = false;

  if(list.length) {
    confirmAdd.disabled = true;
    flavorSubtitle.textContent = 'Select flavor (required)';
    list.forEach(fl=>{
      const b = document.createElement('button');
      b.className = 'flavor';
      b.setAttribute('aria-pressed','false');
      b.textContent = fl;
      b.addEventListener('click', ()=>{
        Array.from(flavorWrap.children).forEach(x=>x.setAttribute('aria-pressed','false'));
        b.setAttribute('aria-pressed','true');
        currentFlavor = fl;
        confirmAdd.disabled = false;
      });
      flavorWrap.appendChild(b);
    });
    if(initialFlavor){
      Array.from(flavorWrap.children).forEach(x=>{ if(x.textContent === initialFlavor) x.click(); });
    }
  } else {
    flavorSubtitle.textContent = 'No flavor options';
  }

  setQty(Math.max(1, parseInt(initialQty,10) || 1));
  pop.classList.add('visible');
  confirmAdd.focus();
}

export function close(){ pop.classList.remove('visible'); }
function setQty(n){ currentQty = n; qtyVal.textContent = String(currentQty); }

src/ui/search-panel.js

import { $, $$ } from '../core/dom.js';
import { searchProducts } from '../api/api.js';
import { openFor } from './flavor-popover.js';
import { navigate } from '../core/router.js';

function attachAddHandlers(root) {
  root.querySelectorAll('.js-add').forEach(btn=>{
    if (btn.id === 'pdpAdd') return;
    if (btn.__hasAddHandler) return;
    btn.__hasAddHandler = true;
    btn.addEventListener('click', (e)=>{ e.stopPropagation(); openFor(btn); });
  });
}

function attachOpenHandlers(root) {
  root.querySelectorAll('#productsList .product').forEach(card=>{
    if (card.__openHandler) return;
    card.__openHandler = true;
    card.addEventListener('click', (e)=>{
      if(e.target.closest('.js-add')) return;
      const id = card.getAttribute('data-id');
      if (id) {
        $('#searchPanel')?.classList.remove('visible');
        navigate('/p/'+id);
      }
    });
  });
}

function renderProductHTML(item){
  const img = (item?.images?.[0]) || 'https://picsum.photos/seed/p/120/120';
  const name = item?.name || 'Unnamed';
  const price = typeof item?.price_cents === 'number' ? (item.price_cents/100).toFixed(2) : '';
  const symbol = (item?.currency || 'EUR') === 'EUR' ? '€' : '';
  const inStock = item?.in_stock;
  const subtitleBits = [];
  if (Array.isArray(item?.categories_names) && item.categories_names.length) subtitleBits.push(item.categories_names[0]);
  if (typeof item?.rating === 'number' && typeof item?.review_count === 'number') subtitleBits.push(`★ ${item.rating.toFixed(1)} (${item.review_count})`);
  const subtitle = subtitleBits.join(' • ');
  return `
    <div class="product" role="listitem" data-id="${item?.id||''}">
      <img src="${img}" alt="${name}" width="64" height="64" style="border-radius:10px">
      <div>
        <div style="font-weight:800">${name}</div>
        <div class="muted" style="font-size:12px">${subtitle || ''} ${inStock?'<span class="badge">In stock</span>':''}</div>
      </div>
      <div style="display:grid;gap:6px;justify-items:end">
        <div class="price">${symbol}${price}</div>
        <button class="add js-add" data-name="${name}" data-flavors='[]'>Add</button>
      </div>
    </div>`;
}

export function mountSearchPanel() {
  const searchInput = $('#search');
  const searchPanel = $('#searchPanel');
  const productsList = $('#productsList');

  // Focus show/hide
  searchInput?.addEventListener('focus', ()=>searchPanel?.classList.add('visible'));
  document.addEventListener('click', (e)=>{
    const inside = e.target.closest('.search-wrap') || e.target.closest('#searchPanel');
    if(!inside) searchPanel?.classList.remove('visible');
  });

  // Toggle chips (static)
  $$('.chip').forEach(chip=>{
    chip.addEventListener('click', ()=>{
      const on = chip.getAttribute('aria-pressed')==='true';
      chip.setAttribute('aria-pressed', String(!on));
    });
  });

  // Live search
  const debounce = (fn, d=350)=>{ let t; return (...a)=>{ clearTimeout(t); t=setTimeout(()=>fn(...a), d); }; };
  async function runSearch(q){
    if(!productsList) return;
    const query = (q||'').trim();
    if(query.length < 2) return;
    productsList.innerHTML = '<div class="muted" style="padding:8px">Searching…</div>';
    try{
      const data = await searchProducts(query, { size: 6 });
      const items = Array.isArray(data?.items) ? data.items : [];
      if(items.length === 0){ productsList.innerHTML = '<div class="muted" style="padding:8px">No results</div>'; return; }
      productsList.innerHTML = items.map(renderProductHTML).join('');
      attachAddHandlers(productsList);
      attachOpenHandlers(productsList);
    }catch(e){
      productsList.innerHTML = '<div class="muted" style="padding:8px">Search failed. Check API.</div>';
    }
  }
  const debounced = debounce(runSearch, 350);
  searchInput?.addEventListener('input', (e)=>debounced(e.target.value));
  searchInput?.addEventListener('keydown', (e)=>{ if(e.key==='Enter'){ e.preventDefault(); runSearch(searchInput.value); } });

  // Initial wiring for static Add buttons
  attachAddHandlers(document);
}

src/ui/pdp.js

import { $, $$ } from '../core/dom.js';
import { bus } from '../core/bus.js';
import { store } from '../core/store.js';
import { getProduct } from '../api/api.js';
import { openFor } from './flavor-popover.js';

const els = {};

function renderStars(val){
  if(typeof val !== 'number' || isNaN(val)) return '★★★★★';
  const full = Math.round(Math.max(0, Math.min(5, val)));
  return '★★★★★'.slice(0, full) + '☆☆☆☆☆'.slice(0, 5-full);
}
function formatPrice(cents, currency='EUR'){
  if(typeof cents !== 'number') return '';
  const symbol = currency === 'EUR' ? '€' : '';
  return symbol + (cents/100).toFixed(2);
}
function extractFlavors(dynamicAttrs){
  try{
    const obj = dynamicAttrs || {};
    const k = Object.keys(obj).find(x=>/flav|taste|maitse/i.test(x));
    if(k && Array.isArray(obj[k])) return obj[k];
  }catch(_){}
  return [];
}

function bindFlavorButtons(container){
  container.querySelectorAll('.flavor').forEach(btn=>{
    btn.addEventListener('click', ()=>{
      container.querySelectorAll('.flavor').forEach(b=>b.setAttribute('aria-pressed','false'));
      btn.setAttribute('aria-pressed','true');
      els.pdpFlavorLabel.textContent = btn.dataset.flavor || btn.textContent;
    });
  });
}

function renderPdpFlavors(list){
  if(!Array.isArray(list) || list.length===0){
    els.pdpFlavors.innerHTML = '';
    els.pdpFlavorLabel.textContent = '';
    els.pdpAdd?.setAttribute('data-flavors','[]');
    return;
  }
  els.pdpFlavors.innerHTML = list.map((fl,idx)=>`<button class="flavor" aria-pressed="${idx===0}" data-flavor="${fl}">${fl}</button>`).join('');
  bindFlavorButtons(els.pdpFlavors);
  els.pdpFlavorLabel.textContent = list[0] || '';
  els.pdpAdd?.setAttribute('data-flavors', JSON.stringify(list));
}

export function mountPdp() {
  // Cache elements once
  Object.assign(els, {
    pdpProductName: $('#pdpProductName'),
    pdpPrice: $('#pdpPrice'),
    pdpStars: $('#pdpStars'),
    pdpReviewCount: $('#pdpReviewCount'),
    pdpFlavors: $('#pdpFlavors'),
    pdpFlavorLabel: $('#pdpFlavorLabel'),
    pdpImage: $('#pdpImage'),
    pdpDescText: $('#pdpDescText'),
    pdpAdd: $('#pdpAdd'),
    pdpQtyMinus: $('#pdpQtyMinus'),
    pdpQtyPlus: $('#pdpQtyPlus'),
    pdpQtyVal: $('#pdpQtyVal'),
  });

  // PDP qty controls
  els.pdpQtyMinus?.addEventListener('click', ()=>{ store.set('pdpQty', Math.max(1, store.get('pdpQty')-1)); els.pdpQtyVal.textContent = String(store.get('pdpQty')); });
  els.pdpQtyPlus?.addEventListener('click', ()=>{ store.set('pdpQty', store.get('pdpQty')+1); els.pdpQtyVal.textContent = String(store.get('pdpQty')); });

  // PDP Add button opens popover with selected flavor + qty
  els.pdpAdd?.addEventListener('click', ()=>{
    const initialFlavor = els.pdpFlavorLabel?.textContent || null;
    openFor(els.pdpAdd, { initialFlavor, initialQty: store.get('pdpQty') });
  });

  // Listen to route changes (open product)
  bus.addEventListener('open-product', async (e)=>{
    const id = e.detail?.id;
    if(!id) return;
    try{
      const prod = await getProduct(id);
      els.pdpProductName.textContent = prod?.name || 'Product';
      els.pdpPrice.textContent = formatPrice(prod?.price_cents, prod?.currency||'EUR');
      els.pdpStars.textContent = renderStars(typeof prod?.rating === 'number' ? prod.rating : undefined);
      els.pdpReviewCount.textContent = String(prod?.review_count ?? '0');
      if (Array.isArray(prod?.images) && prod.images.length) {
        els.pdpImage.src = prod.images[0];
        els.pdpImage.alt = prod?.name || 'Product image';
      }
      if (typeof prod?.search_text === 'string' && prod.search_text) {
        els.pdpDescText.textContent = prod.search_text.slice(0, 320);
      }
      renderPdpFlavors(extractFlavors(prod?.dynamic_attrs));
      els.pdpAdd?.setAttribute('data-name', prod?.name || 'Product');
    } catch(err) {
      console.warn('Failed to open product', err);
    }
  });
}

export function openProduct(id){
  // Bridge for the router → PDP
  bus.dispatchEvent(new CustomEvent('open-product', { detail: { id } }));
}


⸻

Step 5 — app entry

src/main.js

import { $, $$ } from './core/dom.js';
import { bus } from './core/bus.js';
import { store } from './core/store.js';
import { route, start } from './core/router.js';
import { mountFlavorPopover } from './ui/flavor-popover.js';
import { mountSearchPanel } from './ui/search-panel.js';
import { mountPdp, openProduct } from './ui/pdp.js';

// Wire header bits that remain static
function mountHeader() {
  // Command palette (kept minimal)
  const overlay = $('#overlay'), palette = $('#palette'), palInput = $('#palInput'), boltBtn = $('#boltBtn');
  function openPal(){ overlay.classList.add('visible'); palette.classList.add('visible'); palInput.focus(); }
  function closePal(){ overlay.classList.remove('visible'); palette.classList.remove('visible'); palInput.blur(); }
  boltBtn?.addEventListener('click', openPal);
  overlay?.addEventListener('click', closePal);
  document.addEventListener('keydown',(e)=>{
    if((e.metaKey||e.ctrlKey) && e.key.toLowerCase()==='k'){ e.preventDefault(); palette.classList.contains('visible')?closePal():openPal(); }
    if(e.key==='Escape' && palette.classList.contains('visible')) closePal();
  });

  // Reference overlay
  const refBtn = $('#refBtn'), refImage = $('#refImage');
  refBtn?.addEventListener('click', ()=>{
    const on = refImage.classList.toggle('visible');
    refBtn.setAttribute('aria-pressed', String(on));
  });

  // Sync cart pill from store
  const cartCount = $('#cartCount');
  bus.addEventListener('store:cartCount', ()=>{ cartCount.textContent = String(store.get('cartCount')); });
}

// Routes
route('/', ()=>{ /* home/search state — nothing to fetch immediately */ });
route('/p/:id', (id)=>{ openProduct(id); });

// Boot
mountHeader();
mountFlavorPopover();
mountSearchPanel();
mountPdp();
start(); // start router


⸻

Optional — Vite (bundle into one file)

If you want a single optimized bundle:

package.json

{
  "name": "ui-prototype",
  "private": true,
  "scripts": { "dev": "vite", "build": "vite build", "preview": "vite preview" },
  "devDependencies": { "vite": "^5.4.0" }
}

vite.config.js

import { defineConfig } from 'vite';
export default defineConfig({
  root: './',
  build: {
    outDir: 'dist',
    rollupOptions: { input: 'index.html' }
  }
});

Run:

npm i
npm run dev


⸻

Acceptance criteria (Definition of Done)
	•	The app runs with no visual regressions vs. the current index.html.
	•	Routing works:
	•	#/ shows the search panel as before.
	•	Clicking a search result card (not the “Add” button) navigates to #/p/:id and updates the PDP (name, price, stars, image, description, flavors).
	•	Flavor popover opens for:
	•	Search panel “Add” (with/without flavors).
	•	PDP “Add” (preselects current flavor, uses PDP quantity).
	•	Cart counter increments correctly from both popover and PDP, and updates the header pill.
	•	All network I/O is in src/api/api.js. Changing API_BASE in src/config.js is sufficient to point to another backend.
	•	No framework dependency introduced; only ES modules.
	•	Build produces a working dist/ with a single JS bundle when npm run build is used.

⸻

Notes & next steps
	•	You can later wrap the search panel + PDP into Web Components without changing this module layout.
	•	Add src/ui/analytics.js to emit custom events like fp:view_product and fp:add_to_cart for learning.

⸻

That’s it. Create the files above, remove the inline script from index.html, and you’re set. If you want, I can also generate a quick patch to strip the inline script from index.html and insert the <script type="module" …> tag.