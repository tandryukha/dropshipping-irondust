import { $, $$ } from '../core/dom.js';
import { bus } from '../core/bus.js';
import { navigate } from '../core/router.js';
import { searchProducts } from '../api/api.js';
import { isCountBasedForm } from '../core/metrics.js';
import { openFor } from './flavor-popover.js';

const state = {
  page: 1,
  size: 12,
  sort: 'rank',
  preset: 'all',
  loading: false,
  reachedEnd: false
};

let infiniteObserver = null;
let resizeTimer = null;

// Simple deterministic suggestion strategy until vectors:
// - Prefer in-stock items
// - Mix by goal_tags diversity and rating when available
// - Exclude items already rendered in main grid
async function fetchSuggestions({ excludeIds = new Set(), size = 8 } = {}){
  try{
    const base = await searchProducts('', {
      page: 1,
      size: size * 2,
      filters: { in_stock: true },
      sort: ['rating:desc', 'review_count:desc']
    });
    const items = Array.isArray(base?.items) ? base.items : [];
    const filtered = items.filter(it => it && !excludeIds.has(it.id));
    // Light diversification by goal tag
    const seenGoals = new Set();
    const diverse = [];
    for (const it of filtered){
      const g = Array.isArray(it.goal_tags) && it.goal_tags.length ? it.goal_tags[0] : '';
      const key = g || 'other';
      const countForGoal = diverse.filter(d => (Array.isArray(d.goal_tags) && d.goal_tags[0]) === g).length;
      if (countForGoal < 3) diverse.push(it);
      if (diverse.length >= size) break;
      seenGoals.add(key);
    }
    return diverse.slice(0, size);
  }catch(_e){ return []; }
}

function sortToBackend(sortKey){
  switch (sortKey) {
    case 'price_asc': return ['price_cents:asc'];
    case 'price_desc': return ['price_cents:desc'];
    case 'popular': return ['rating:desc', 'review_count:desc'];
    case 'rank':
    default: return undefined; // backend default / merchandising
  }
}

function presetToFilters(key){
  switch (key) {
    case 'strength':   return { in_stock: true, goal_tags: ['strength'] };
    case 'endurance':  return { in_stock: true, goal_tags: ['endurance'] };
    case 'wellness':   return { in_stock: true, goal_tags: ['wellness'] };
    case 'sale':       return { in_stock: true, is_on_sale: true };
    case 'all':
    default:           return { in_stock: true };
  }
}

function renderCardHTML(item){
  const id = item?.id || '';
  const img = (item?.images?.[0]) || 'https://picsum.photos/seed/home/200/200';
  const name = item?.name || 'Product';
  const priceNum = typeof item?.price_cents === 'number' ? (item.price_cents/100) : null;
  const price = priceNum != null ? priceNum.toFixed(2).replace('.', ',') : '';
  const symbol = (item?.currency || 'EUR') === 'EUR' ? '€' : '';
  const sub = [];
  if (Array.isArray(item?.goal_tags) && item.goal_tags.length) sub.push(item.goal_tags[0]);
  if (typeof item?.form === 'string' && item.form) sub.push(item.form);
  if (typeof item?.rating === 'number' && typeof item?.review_count === 'number') {
    if (item.review_count >= 3) sub.push(`★ ${item.rating.toFixed(1)} (${item.review_count})`);
  }
  // price metrics
  const isCount = isCountBasedForm(item?.form);
  const pps = typeof item?.price_per_serving === 'number' ? ` • ${symbol}${item.price_per_serving.toFixed(2).replace('.', ',')}/serv` : '';
  const p100 = (!isCount && typeof item?.price_per_100g === 'number') ? ` • ${symbol}${item.price_per_100g.toFixed(2).replace('.', ',')}/100g` : '';
  const metrics = pps || p100 ? `<div class="sub">${pps ? pps.slice(3) : ''}${pps && p100 ? ' • ' : ''}${p100 ? p100.slice(3) : ''}</div>` : '';
  // Compute flavors list and show chooser only when there are multiple options
  const flavorsList = (()=>{
    if (Array.isArray(item?.dynamic_attrs?.flavors)) return item.dynamic_attrs.flavors;
    const list = [];
    if (typeof item?.flavor === 'string' && item.flavor.trim()) list.push(item.flavor.trim());
    return list;
  })();
  const btnLabel = (Array.isArray(flavorsList) && flavorsList.length > 1) ? 'Choose flavor' : 'Add';
  const sale = item?.is_on_sale === true && typeof item?.discount_pct === 'number' ? `<span class="badge-sale">-${Math.round(item.discount_pct)}%</span>` : '';
  return `
    <div class="home-card" data-id="${id}" role="listitem">
      <img src="${img}" alt="${name}" width="80" height="80" style="border-radius:12px">
      <div class="meta">
        <div class="title" style="font-weight:700">${name} ${sale}</div>
        <div class="sub">${sub.join(' • ')}</div>
        ${metrics}
        <div class="price-add">
          <div style="font-weight:800">${symbol}${price}</div>
          <button class="add js-add" aria-label="Add to cart" title="Add to cart" data-name="${name}" data-flavors='${JSON.stringify(flavorsList)}'>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6h15l-1.5 9h-12L5 3H2"/><circle cx="9" cy="20" r="1.75"/><circle cx="18" cy="20" r="1.75"/></svg>
          </button>
        </div>
      </div>
    </div>`;
}

function bindHandlers(container){
  // Add-to-cart popover
  container.querySelectorAll('.js-add').forEach(btn=>{
    if (btn.__addBound) return; btn.__addBound = true;
    btn.addEventListener('click', (e)=>{
      e.stopPropagation();
      const list = JSON.parse(btn.dataset.flavors || '[]');
      if (Array.isArray(list) && list.length === 1) {
        openFor(btn, { initialFlavor: list[0] });
      } else {
        openFor(btn);
      }
    });
  });
  // Navigate to PDP on card click
  container.querySelectorAll('.home-card').forEach(card=>{
    if (card.__navBound) return; card.__navBound = true;
    card.addEventListener('click', (e)=>{
      if (e.target.closest('.js-add')) return;
      const id = card.getAttribute('data-id');
      if (id) navigate('/p/'+id);
    });
  });
}

async function fetchPage({ append=false }={}){
  if (state.loading || state.reachedEnd) return;
  state.loading = true;
  const grid = $('#homeGrid');
  // Hide suggestions when fetching a fresh dataset
  if (!append) { const sugWrap = $('#homeSuggestions'); const sugGrid = $('#homeSugGrid'); if (sugWrap) sugWrap.style.display = 'none'; if (sugGrid) sugGrid.innerHTML = ''; }
  if (!append) grid.innerHTML = '';
  const loader = document.createElement('div');
  loader.className = 'muted';
  loader.style.padding = '8px';
  loader.textContent = 'Loading…';
  grid.parentElement.insertBefore(loader, grid.nextSibling);
  try{
    const res = await searchProducts('', {
      page: state.page,
      size: state.size,
      filters: presetToFilters(state.preset),
      sort: sortToBackend(state.sort)
    });
    const items = Array.isArray(res?.items) ? res.items : [];
    if (!append && items.length === 0) {
      grid.innerHTML = '<div class="muted" style="padding:8px">No items yet</div>';
      state.reachedEnd = true;
      // Show fallback suggestions on empty state
      renderSuggestions({ exclude: new Set() });
      return;
    }
    const html = items.map(renderCardHTML).join('');
    if (append) grid.insertAdjacentHTML('beforeend', html); else grid.innerHTML = html;
    bindHandlers(grid);
    if (items.length < state.size) state.reachedEnd = true;
    // If reached end, surface suggestions after grid
    if (state.reachedEnd) {
      const ids = new Set();
      $$('#homeGrid .home-card').forEach(card=>{ const id = card.getAttribute('data-id'); if (id) ids.add(id); });
      renderSuggestions({ exclude: ids });
    }
  } catch(err){
    console.warn('Home fetch failed', err);
    if (!append) grid.innerHTML = '<div class="muted" style="padding:8px">Failed to load. Check API.</div>';
  } finally {
    loader.remove();
    state.loading = false;
  }
}

function ensureViewportFilled({ bufferPx=600, maxPages=5 }={}){
  const home = $('#home');
  if (!home || !home.classList.contains('visible')) return;
  let attempts = 0;
  const needMore = ()=>{
    const doc = document.documentElement;
    const pageHeight = Math.max(doc.scrollHeight, doc.clientHeight);
    return !state.reachedEnd && (pageHeight < window.innerHeight + bufferPx);
  };
  const loadUntilFilled = async ()=>{
    while (needMore() && attempts < maxPages && !state.loading) {
      state.page += 1;
      attempts += 1;
      // eslint-disable-next-line no-await-in-loop
      await fetchPage({ append:true });
    }
  };
  loadUntilFilled();
}

function setupInfiniteScroll(){
  const sentinelWrap = document.querySelector('.home-load');
  const moreBtn = $('#homeMore');
  if (moreBtn) moreBtn.style.display = 'none';
  if (!sentinelWrap) return;
  sentinelWrap.style.minHeight = '1px';
  if (infiniteObserver) { try { infiniteObserver.disconnect(); } catch(_){} }
  infiniteObserver = new IntersectionObserver((entries)=>{
    const seen = entries.some(e=>e.isIntersecting);
    if (!seen) return;
    if (state.loading || state.reachedEnd) return;
    state.page += 1;
    fetchPage({ append:true });
  }, { root: null, rootMargin: '800px 0px', threshold: 0 });
  infiniteObserver.observe(sentinelWrap);
}

export function mountHome(){
  const section = $('#home');
  const sortSel = $('#homeSort');
  const moreBtn = $('#homeMore');
  const sugWrap = $('#homeSuggestions');

  // Preset chips
  $$('#home .chip[data-home-preset]').forEach(ch=>{
    if (ch.__presetBound) return; ch.__presetBound = true;
    ch.addEventListener('click', ()=>{
      $$('#home .chip[data-home-preset]').forEach(x=>x.setAttribute('aria-pressed','false'));
      ch.setAttribute('aria-pressed','true');
      state.preset = ch.getAttribute('data-home-preset');
      state.page = 1; state.reachedEnd = false;
      fetchPage({ append:false }).then(()=> ensureViewportFilled());
    });
  });

  // Default active preset
  const def = $('#home .chip[data-home-preset="all"]');
  if (def) def.setAttribute('aria-pressed','true');

  // Sort
  sortSel?.addEventListener('change', (e)=>{
    state.sort = e.target.value;
    state.page = 1; state.reachedEnd = false;
    // Hide suggestions on new sort
    if (sugWrap) { sugWrap.style.display = 'none'; $('#homeSugGrid').innerHTML = ''; }
    fetchPage({ append:false }).then(()=> ensureViewportFilled());
  });

  // Infinite scroll sentinel
  setupInfiniteScroll();

  // Initial load
  if (section) fetchPage({ append:false }).then(()=> ensureViewportFilled());

  // Re-fetch current page when language changes
  bus.addEventListener('language:changed', () => {
    state.page = 1; state.reachedEnd = false;
    fetchPage({ append:false }).then(()=> ensureViewportFilled());
  });

  // Re-check after resize (debounced)
  window.addEventListener('resize', ()=>{
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(()=>{
      ensureViewportFilled();
    }, 200);
  });
}

export function showHome(){ const s = $('#home'); if (s) s.classList.add('visible'); }
export function hideHome(){ const s = $('#home'); if (s) s.classList.remove('visible'); }

async function renderSuggestions({ exclude = new Set() } = {}){
  const wrap = $('#homeSuggestions');
  const grid = $('#homeSugGrid');
  if (!wrap || !grid) return;
  const items = await fetchSuggestions({ excludeIds: exclude, size: 8 });
  if (items.length === 0) { wrap.style.display = 'none'; grid.innerHTML = ''; return; }
  grid.innerHTML = items.map(renderCardHTML).join('');
  bindHandlers(grid);
  wrap.style.display = '';
}


