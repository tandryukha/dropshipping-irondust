import { $, $$ } from '../core/dom.js';
import { bus } from '../core/bus.js';
import { searchProducts } from '../api/api.js';
import { openFor } from './flavor-popover.js';
import { derivePricePer100g, isCountBasedForm } from '../core/metrics.js';
import { navigate } from '../core/router.js';

// State management
const searchState = {
  activeFilters: new Map(),
  activePreset: null,
  sortOrder: 'relevance',
  goalsMultiSelect: false,
  activeGoals: [], // labels of currently applied multi-goal selection
  lastAppliedSignature: '' // for Apply button enablement (optional)
};

// Helpers to compare filters across duplicate chips
function canonicalize(value){
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value && typeof value === 'object'){
    const out = {};
    Object.keys(value).sort().forEach(k=>{ out[k]=canonicalize(value[k]); });
    return out;
  }
  return value;
}

function getChipFilterObject(el){
  try{ return JSON.parse(el.getAttribute('data-filter')||'{}'); }catch(_e){ return {}; }
}

function areFiltersEqual(a,b){
  try{ return JSON.stringify(canonicalize(a)) === JSON.stringify(canonicalize(b)); }catch(_e){ return false; }
}

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
      e.stopPropagation();
      const id = card.getAttribute('data-id');
      if (id) {
        // Hide the search panel when navigating to product
        const sp = $('#searchPanel');
        sp?.classList.remove('visible');
        const si = $('#search');
        si?.blur();
        navigate('/p/'+id);
      }
    });
  });
}

function escapeHtml(text){
  if (text == null) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function buildHighlighter(query){
  const q = (query||'').trim();
  if (q.length < 2) return (s)=>escapeHtml(s);
  const terms = Array.from(new Set(q.split(/\s+/).filter(t=>t && t.length>1)));
  if (terms.length === 0) return (s)=>escapeHtml(s);
  const escaped = terms.map(t=>t.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'));
  const re = new RegExp('(' + escaped.join('|') + ')', 'ig');
  return (s)=>escapeHtml(s).replace(re, '<span class="hl">$1</span>');
}

function renderProductHTML(item, query){
  const img = (item?.images?.[0]) || 'https://picsum.photos/seed/p/120/120';
  const name = item?.name || 'Unnamed';
  const highlight = buildHighlighter(query);
  const price = typeof item?.price_cents === 'number' ? (item.price_cents/100).toFixed(2).replace('.', ',') : '';
  const symbol = (item?.currency || 'EUR') === 'EUR' ? '€' : '';
  const inStock = item?.in_stock;
  const subtitleBits = [];
  if (Array.isArray(item?.categories_names) && item.categories_names.length) subtitleBits.push(item.categories_names[0]);
  const form = (typeof item?.form === 'string' ? item.form : '');
  if (form) subtitleBits.push(form);
  const isCountBased = isCountBasedForm(form);
  if (isCountBased) {
    if (typeof item?.unit_count === 'number') subtitleBits.push(`${item.unit_count} pcs`);
    if (typeof item?.price_per_unit === 'number') subtitleBits.push(`${symbol}${item.price_per_unit.toFixed(2).replace('.', ',')}/unit`);
  } else {
    if (typeof item?.serving_size_g === 'number') subtitleBits.push(`${item.serving_size_g} g/serv`);
    if (typeof item?.price_per_serving === 'number') subtitleBits.push(`€${item.price_per_serving.toFixed(2).replace('.', ',')}/serv`);
    // Append €/100g when reliably derivable
    const p100 = derivePricePer100g(item);
    if (typeof p100 === 'number' && p100 > 0) subtitleBits.push(`€${p100.toFixed(2).replace('.', ',')}/100g`);
  }
  if (typeof item?.rating === 'number' && typeof item?.review_count === 'number') {
    if (item.review_count >= 3) subtitleBits.push(`★ ${item.rating.toFixed(1)} (${item.review_count})`);
  }
  const hasVariants = (Array.isArray(item?.dynamic_attrs?.flavors) && item.dynamic_attrs.flavors.length) || (typeof item?.flavor === 'string' && item.flavor.trim());
  const btnLabel = hasVariants ? 'Choose flavor' : 'Add';
  const subtitle = subtitleBits.join(' • ');
  const salePct = (item?.is_on_sale === true && typeof item?.discount_pct === 'number') ? Math.round(item.discount_pct) : null;
  const sale = (typeof salePct === 'number' && salePct > 0) ? `<span class="badge" style="background:#fff3f3;border-color:#ffc9c9;color:#b91c1c">-${salePct}%</span>` : '';
  const vegan = Array.isArray(item?.diet_tags) && item.diet_tags.includes('vegan');
  const sugarFree = Array.isArray(item?.diet_tags) && item.diet_tags.includes('sugar_free');
  const snippet = (typeof item?.benefit_snippet === 'string' ? item.benefit_snippet : '').trim();
  const snippetLine = snippet ? `<div class="muted benefit-snippet" style="font-size:12px;line-height:1.5;margin-top:4px">${highlight(snippet.length > 160 ? (snippet.slice(0, 157).trim()+"…") : snippet)}</div>` : '';
  // Split price for richer styling
  const [maj, minPart] = String(price||'').split(',');
  const min = typeof minPart === 'string' ? (','+minPart) : '';
  return `
    <div class="product" role="listitem" data-id="${item?.id||''}">
      <div class="media">
        <img src="${img}" alt="${name}" width="72" height="72" style="border-radius:10px">
        <div class="badges">
          ${vegan?'<span class="pill-badge vegan">Vegan</span>':''}
          ${sugarFree?'<span class="pill-badge sugar">Sugar-free</span>':''}
          ${typeof salePct==='number' && salePct>0?'<span class="pill-badge sale">-'+salePct+'%</span>':''}
        </div>
      </div>
      <div>
        <div class="title">${highlight(name)} ${sale}</div>
        ${snippetLine}
        <div class="muted" style="font-size:12px">${subtitle || ''} ${inStock?'<span class="badge">In stock</span>':''}</div>
      </div>
      <div style="display:grid;gap:10px;justify-items:end">
        <div class="price">${symbol?`<span class=\"cur\">${symbol}</span>`:''}${price?`<span class=\"maj\">${maj||''}</span><span class=\"min\">${min||''}</span>`:''}</div>
        <button class="add js-add" aria-label="Add to cart" title="Add to cart" data-name="${name}" data-flavors='${JSON.stringify((()=>{ const list=[]; if(Array.isArray(item?.dynamic_attrs?.flavors)) return item.dynamic_attrs.flavors; if(typeof item?.flavor === "string" && item.flavor.trim()) list.push(item.flavor.trim()); return list; })())}'>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6h15l-1.5 9h-12L5 3H2"/><circle cx="9" cy="20" r="1.75"/><circle cx="18" cy="20" r="1.75"/></svg>
        </button>
      </div>
    </div>`;
}

// Applied filters bar management
function updateAppliedBar() {
  const appliedBar = $('#appliedBar');
  if (!appliedBar) return;
  
  const filters = Array.from(searchState.activeFilters.entries());
  
  if (filters.length === 0 && !searchState.activePreset) {
    appliedBar.style.display = 'none';
    appliedBar.innerHTML = '';
    return;
  }
  
  appliedBar.style.display = '';
  appliedBar.innerHTML = '';
  
  // Show active preset
  if (searchState.activePreset) {
    const chip = document.createElement('div');
    chip.className = 'applied-chip';
    chip.innerHTML = `
      <span>${searchState.activePreset}</span>
      <button aria-label="Remove preset">×</button>
    `;
    chip.querySelector('button').addEventListener('click', (e) => {
      e.stopPropagation();
      clearPreset();
      updateAppliedBar();
      if (window.__runFilteredSearch) window.__runFilteredSearch();
    });
    appliedBar.appendChild(chip);
  }
  // Show active multi-goal selection summary when no single preset label
  if (!searchState.activePreset && Array.isArray(searchState.activeGoals) && searchState.activeGoals.length > 0) {
    const chip = document.createElement('div');
    chip.className = 'applied-chip';
    chip.innerHTML = `
      <span>Goals: ${searchState.activeGoals.join(', ')}</span>
      <button aria-label="Clear goals">×</button>
    `;
    chip.querySelector('button').addEventListener('click', (e) => {
      e.stopPropagation();
      // Clear only goal_tags from preset filters
      const merged = { ...(searchState.__presetFilters||{}) };
      delete merged.goal_tags;
      searchState.__presetFilters = merged;
      searchState.activeGoals = [];
      // Also clear selection on top-level panel cards and modal
      $$('#searchPanel .preset-grid .preset-card.active').forEach(card=>card.classList.remove('active'));
      $$('#goalsModal .preset-card.active').forEach(card=>card.classList.remove('active'));
      updateAppliedBar();
      if (window.__runFilteredSearch) window.__runFilteredSearch();
    });
    appliedBar.appendChild(chip);
  }
  
  // Show first 3 filters
  const visibleFilters = filters.slice(0, 3);
  visibleFilters.forEach(([chipElement, label]) => {
    const chip = document.createElement('div');
    chip.className = 'applied-chip';
    chip.innerHTML = `
      <span>${label}</span>
      <button aria-label="Remove filter">×</button>
    `;
    chip.querySelector('button').addEventListener('click', (e) => {
      e.stopPropagation();
      // If the underlying chip got detached or re-rendered, find by filter object and remove
      if (!document.body.contains(chipElement)) {
        // Build a virtual element with the same filter for removal routine
        const v = document.createElement('button');
        v.setAttribute('role','switch');
        v.setAttribute('data-filter', JSON.stringify(getChipFilterObject(chipElement)));
        removeFilter(v);
      } else {
        removeFilter(chipElement);
      }
      updateAppliedBar();
      if (window.__runFilteredSearch) window.__runFilteredSearch();
    });
    appliedBar.appendChild(chip);
  });
  
  // Show +N more if needed
  if (filters.length > 3) {
    const more = document.createElement('span');
    more.className = 'applied-more';
    more.textContent = `+${filters.length - 3} more`;
    appliedBar.appendChild(more);
  }
}

function removeFilter(chipElement) {
  // Turn off ALL chips that correspond to the same filter object as the removed one
  if (chipElement) {
    const targetFilter = getChipFilterObject(chipElement);
    $$('.chip[role="switch"]').forEach(c=>{
      const cf = getChipFilterObject(c);
      if (areFiltersEqual(cf, targetFilter)){
        c.setAttribute('aria-pressed','false');
        searchState.activeFilters.delete(c);
      }
    });
  }
  updateAppliedBar();
  // Recalculate overflow and rerun search to keep results in sync
  if (window.checkChipOverflow) window.checkChipOverflow();
  if (window.__runFilteredSearch) window.__runFilteredSearch();
}

function clearPreset() {
  searchState.activePreset = null;
  searchState.__presetFilters = null;
  $$('.preset-card').forEach(card => card.classList.remove('active'));
  // Clear all filters associated with preset
  searchState.activeFilters.clear();
  $$('.chip[role="switch"]').forEach(chip => chip.setAttribute('aria-pressed', 'false'));
  updateAppliedBar();
}

// Chip overflow management
function setupChipOverflow() {
  const chipTray = $('#quickFilters');
  if (!chipTray) return;
  
  const overflowChip = $('#quickOverflow');
  
  function measureOverflowWidth(count) {
    if (!overflowChip) return 0;
    const prev = overflowChip.querySelector('span')?.textContent || '';
    const prevDisplay = overflowChip.style.display;
    const prevVisibility = overflowChip.style.visibility;
    const prevPosition = overflowChip.style.position;
    const prevLeft = overflowChip.style.left;

    // Prepare for measurement
    overflowChip.style.display = '';
    overflowChip.style.visibility = 'hidden';
    overflowChip.style.position = 'absolute';
    overflowChip.style.left = '-9999px';
    overflowChip.querySelector('span').textContent = String(count);
    const width = overflowChip.offsetWidth;

    // Restore
    overflowChip.querySelector('span').textContent = prev;
    overflowChip.style.display = prevDisplay;
    overflowChip.style.visibility = prevVisibility;
    overflowChip.style.position = prevPosition;
    overflowChip.style.left = prevLeft;
    return width;
  }

  function checkOverflow() {
    const chips = Array.from(chipTray.querySelectorAll('.chip:not(.chip-overflow)'));
    if (!overflowChip || chips.length === 0) return;
    
    // Reset all chips to visible first
    chips.forEach(chip => {
      chip.style.display = '';
    });
    overflowChip.style.display = 'none';
    
    // Get the container width
    const trayWidth = chipTray.offsetWidth;
    if (trayWidth === 0) return; // Container not rendered yet
    
    // Calculate which chips fit
    let currentWidth = 0;
    let firstHiddenIndex = -1;
    const chipGap = 6;
    // First pass: optimistic measurement using 2 digits as placeholder
    let overflowWidth = measureOverflowWidth(99) || 50;
    
    // Find how many chips fit
    for (let i = 0; i < chips.length; i++) {
      const chip = chips[i];
      const chipWidth = chip.offsetWidth;
      const totalWidth = currentWidth + chipWidth + (i > 0 ? chipGap : 0);
      
      // Check if adding this chip plus overflow chip would exceed width
      if (totalWidth + overflowWidth + chipGap > trayWidth) {
        firstHiddenIndex = i;
        break;
      }
      
      currentWidth = totalWidth;
    }
    
    // Hide chips that don't fit and show overflow
    if (firstHiddenIndex !== -1) {
      let hiddenCount = chips.length - firstHiddenIndex;
      // Second pass: measure actual overflow width with correct digits
      overflowWidth = measureOverflowWidth(hiddenCount) || overflowWidth;

      // Re-run fitting with accurate overflow width
      currentWidth = 0;
      firstHiddenIndex = -1;
      for (let i = 0; i < chips.length; i++) {
        const chip = chips[i];
        const chipWidth = chip.offsetWidth;
        const totalWidth = currentWidth + chipWidth + (i > 0 ? chipGap : 0);
        if (totalWidth + overflowWidth + chipGap > trayWidth) { firstHiddenIndex = i; break; }
        currentWidth = totalWidth;
      }
      hiddenCount = firstHiddenIndex === -1 ? 0 : (chips.length - firstHiddenIndex);
      
      // Hide overflowing chips
      if (firstHiddenIndex !== -1) {
        for (let i = firstHiddenIndex; i < chips.length; i++) {
          chips[i].style.display = 'none';
        }
      }
      
      // Show overflow chip with count
      if (hiddenCount > 0) {
        overflowChip.style.display = '';
        overflowChip.querySelector('span').textContent = hiddenCount;
      } else {
        overflowChip.style.display = 'none';
      }
    } else {
      // Everything fits; ensure overflow hidden and count cleared
      overflowChip.style.display = 'none';
      overflowChip.querySelector('span').textContent = '0';
    }
  }
  
  // Debounce resize handler
  let resizeTimeout;
  function handleResize() {
    clearTimeout(resizeTimeout);
    resizeTimeout = setTimeout(checkOverflow, 100);
  }
  
  // Check on load and resize
  setTimeout(checkOverflow, 0); // Defer to ensure DOM is ready
  window.addEventListener('resize', handleResize);
  
  // Expose checkOverflow function for re-checking when panel becomes visible
  window.checkChipOverflow = checkOverflow;
  
  // Handle overflow click - show all filters modal
  overflowChip?.addEventListener('click', () => {
    const allFiltersBtn = $('#allFiltersBtn');
    if (allFiltersBtn) allFiltersBtn.click();
  });
}

// Exposed controls for overlay
function setOverlayAria(open){
  const panel = $('#searchPanel');
  if (!panel) return;
  panel.setAttribute('aria-hidden', open ? 'false' : 'true');
}

function serializeFilters(obj){
  try{
    const parts = [];
    const keys = Object.keys(obj||{}).sort();
    keys.forEach((k)=>{
      const v = obj[k];
      if (Array.isArray(v)) {
        const arr = v.slice().map(x=>String(x)).sort();
        parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(arr.join(','))}`);
      } else if (v && typeof v === 'object' && 'op' in v && 'value' in v) {
        parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v.op)+':'+String(v.value))}`);
      } else if (typeof v !== 'undefined' && v !== null) {
        parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
      }
    });
    return parts.join('&');
  }catch(_e){ return ''; }
}

function buildSearchPath(query, filters){
  const qsParts = [];
  if (query) qsParts.push('q='+encodeURIComponent(query));
  const fqs = serializeFilters(filters||{});
  if (fqs) qsParts.push(fqs);
  return '/search' + (qsParts.length? ('?'+qsParts.join('&')):'');
}

function parseHashQuery(){
  try{
    const h = (window.location && window.location.hash || '');
    const qIndex = h.indexOf('?');
    if (qIndex === -1) return new URLSearchParams('');
    const qs = h.slice(qIndex+1);
    return new URLSearchParams(qs);
  }catch(_e){ return new URLSearchParams(''); }
}

function updateResultCount(n){
  const el = $('#resultCount');
  if (el) el.textContent = String(n||0);
}

function markChangedSinceApply(){
  const applyBtn = $('#applyFooterBtn');
  if (!applyBtn) return;
  // We run live updates, keep disabled; still toggle visual if needed
  applyBtn.disabled = true;
}

function restorePersisted(){
  try{
    const raw = localStorage.getItem('ui_quick_filters');
    if (!raw) return;
    const state = JSON.parse(raw);
    if (!state || typeof state !== 'object') return;
    $$('.chip[role="switch"]').forEach(chip=>{
      const f = getChipFilterObject(chip);
      const key = JSON.stringify(canonicalize(f));
      if (state[key] === true) chip.setAttribute('aria-pressed','true');
    });
  }catch(_e){ /* ignore */ }
}

function persistQuickFilters(){
  try{
    const map = {};
    $$('.chip[role="switch"]').forEach(chip=>{
      const f = getChipFilterObject(chip);
      const key = JSON.stringify(canonicalize(f));
      map[key] = chip.getAttribute('aria-pressed') === 'true';
    });
    localStorage.setItem('ui_quick_filters', JSON.stringify(map));
  }catch(_e){ /* ignore */ }
}

function persistRecentQuery(q){
  try{
    const key = 'ui_recent_searches';
    const arr = JSON.parse(localStorage.getItem(key) || '[]');
    const next = [q, ...arr.filter(x=>x && x!==q)].slice(0,5);
    localStorage.setItem(key, JSON.stringify(next));
  }catch(_e){ /* ignore */ }
}

function renderPreInputSuggestions(){
  const box = $('#preInputSuggestions');
  if (!box) return;
  const key = 'ui_recent_searches';
  let arr = [];
  try{ arr = JSON.parse(localStorage.getItem(key) || '[]'); } catch(_e){}
  const tries = ['creatine', 'whey isolate', 'vegan protein', 'electrolytes'];
  const items = (arr.length ? arr : tries).slice(0,5);
  box.style.display = '';
  box.innerHTML = `
    <div style="font-weight:800;margin-bottom:6px">${arr.length? 'Recent':'Try:'}</div>
    <div class="chip-group">${items.map(t=>`<button class="chip" data-try="${t}">${t}</button>`).join('')}</div>
  `;
  box.querySelectorAll('button[data-try]').forEach(b=>{
    b.addEventListener('click', ()=>{
      const input = $('#searchOverlayInput');
      if (input) { input.value = b.getAttribute('data-try'); input.dispatchEvent(new Event('input')); input.focus(); }
    });
  });
}

export function mountSearchPanel() {
  const headerInput = $('#search');
  const overlayInput = $('#searchOverlayInput');
  const searchPanel = $('#searchPanel');
  const productsList = $('#productsList');
  const allChips = $$('.chip[role="switch"]');
  const sortDropdown = $('#sortDropdown');
  const sortDropdownHeader = $('#sortDropdownHeader');
  const overlayFiltersBtn = $('#overlayFiltersBtn');
  const overlayClearBtn = $('#overlayClearBtn');
  const closeOverlayBtn = $('#closeOverlayBtn');
  const applyFooterBtn = $('#applyFooterBtn');
  const panelInner = document.querySelector('.panel-inner');

  // Overlay paging state for infinite scroll
  const oState = {
    page: 1,
    size: 10,
    loading: false,
    reachedEnd: false,
    observer: null
  };

  function filtersSignature(obj){
    try{ return JSON.stringify(canonicalize(obj||{})); }catch(_e){ return ''; }
  }

  function ensureSentinel(){
    let sentinel = document.getElementById('overlayMoreSentinel');
    if (!sentinel){
      sentinel = document.createElement('div');
      sentinel.id = 'overlayMoreSentinel';
      sentinel.style.minHeight = '1px';
      productsList?.parentElement?.appendChild(sentinel);
    }
    return sentinel;
  }

  function setupOverlayInfinite(){
    const sentinel = ensureSentinel();
    if (!panelInner || !sentinel) return;
    if (oState.observer) { try { oState.observer.disconnect(); } catch(_e){} }
    oState.observer = new IntersectionObserver((entries)=>{
      const seen = entries.some(e=>e.isIntersecting);
      if (!seen || oState.loading || oState.reachedEnd) return;
      oState.page += 1;
      performSearch({ append:true });
    }, { root: document.getElementById('productsCol') || panelInner, rootMargin: '800px 0px', threshold: 0 });
    oState.observer.observe(sentinel);
  }
  
  // Detect if current route is PDP
  function isOnPdp(){
    try{
      const h = (window.location && window.location.hash || '').slice(1) || '/';
      return /^\/p\/[^/]+$/.test(h);
    }catch(_e){ return false; }
  }
  
  // Setup chip overflow
  setupChipOverflow();
  
  // Handle sort dropdowns (panel and header) in sync
  sortDropdown?.addEventListener('change', (e) => {
    searchState.sortOrder = e.target.value;
    if (sortDropdownHeader) sortDropdownHeader.value = searchState.sortOrder;
    runFilteredSearch();
  });
  sortDropdownHeader?.addEventListener('change', (e) => {
    searchState.sortOrder = e.target.value;
    if (sortDropdown) sortDropdown.value = searchState.sortOrder;
    runFilteredSearch();
  });

  let lastOpenedAt = 0;
  function openOverlay() {
    if (!searchPanel) return;
    searchPanel.classList.add('visible');
    setOverlayAria(true);
    lastOpenedAt = Date.now();
    try{ document.body.classList.add('no-scroll'); }catch(_e){}
    // Compute sticky offset for side columns based on header height
    try{
      const top = document.getElementById('panelTop');
      if (top) {
        const h = top.getBoundingClientRect().height;
        document.documentElement.style.setProperty('--panel-top-offset', Math.ceil(h) + 'px');
      }
    }catch(_e){}
    setTimeout(() => {
      const checkOverflow = window.checkChipOverflow;
      if (checkOverflow) checkOverflow();
    }, 10);
    (overlayInput||headerInput)?.focus();
  }
  function closeOverlay() {
    if (!searchPanel) return;
    searchPanel.classList.remove('visible');
    setOverlayAria(false);
    try{ document.body.classList.remove('no-scroll'); }catch(_e){}
  }
  // expose for router
  window.__openSearchOverlay = openOverlay;
  window.__closeSearchOverlay = closeOverlay;

  // Focus show/hide
  headerInput?.addEventListener('focus', ()=>{
    // Do not auto-open on load; open only when user interacts
    openOverlay();
  });
  document.addEventListener('click', (e)=>{
    // Don't close if clicking inside modals
    if (e.target.closest('.modal') || e.target.closest('.modal-overlay')) return;
    // Ignore the very first click that opened the overlay (mouseup after mousedown-focus)
    if (searchPanel?.classList.contains('visible') && Date.now() - lastOpenedAt < 250) return;
    const inside = e.target.closest('.panel-inner') || e.target.closest('.search-wrap');
    if(!inside) closeOverlay();
  });
  // Close when clicking backdrop area of panel
  searchPanel?.addEventListener('click', (e)=>{
    if (e.target === searchPanel) closeOverlay();
  });
  closeOverlayBtn?.addEventListener('click', closeOverlay);
  overlayFiltersBtn?.addEventListener('click', ()=>{
    const allFiltersBtn = $('#allFiltersBtn');
    if (allFiltersBtn) allFiltersBtn.click();
  });
  overlayClearBtn?.addEventListener('click', ()=>{
    // Clear input and all filters
    if (overlayInput) { overlayInput.value=''; }
    if (headerInput) { headerInput.value=''; }
    clearPreset();
    searchState.activeFilters.clear();
    $$('.chip[role="switch"]').forEach(chip => chip.setAttribute('aria-pressed', 'false'));
    updateAppliedBar();
    if (window.checkChipOverflow) window.checkChipOverflow();
    productsList.innerHTML = '';
    renderPreInputSuggestions();
    markChangedSinceApply();
    persistQuickFilters();
    // Update URL
    navigate('/search');
  });
  // Hotkeys: '/' to focus, Esc closes
  document.addEventListener('keydown', (e)=>{
    const key = e.key;
    if (key === '/' && !e.metaKey && !e.ctrlKey && !e.altKey) {
      e.preventDefault();
      openOverlay();
      (overlayInput||headerInput)?.focus();
    }
    if (key === 'Escape' && searchPanel?.classList.contains('visible')) {
      e.preventDefault();
      closeOverlay();
    }
  });
  
  // Handle preset cards (both in panel and modal)
  function setupPresetCards() {
    $$('.preset-card').forEach(card => {
      if (card.__hasPresetHandler) return;
      card.__hasPresetHandler = true;
      
      card.addEventListener('click', () => {
        // In goals modal multi-select mode, do not run single-select preset behavior
        const inModal = !!card.closest('#goalsModal');
        if (searchState.goalsMultiSelect && inModal) return;

        // In main panel (not modal), support multi-select for high-level goals
        if (!inModal && card.closest('.preset-grid')) {
          card.classList.toggle('active');
          const activeTop = $$('#searchPanel .preset-grid .preset-card.active');
          const selectedPanelTags = [];
          const selectedPanelLabels = [];
          activeTop.forEach(c => {
            const f = JSON.parse(c.getAttribute('data-filters')||'{}');
            const tags = Array.isArray(f.goal_tags) ? f.goal_tags : [];
            tags.forEach(t=>{ if(!selectedPanelTags.includes(t)) selectedPanelTags.push(t); });
            const lbl = c.querySelector('.preset-label')?.textContent?.trim();
            if (lbl && !selectedPanelLabels.includes(lbl)) selectedPanelLabels.push(lbl);
          });
          // Determine all tags represented in the top panel
          const panelAllTags = [];
          $$('#searchPanel .preset-grid .preset-card').forEach(c => {
            const f = JSON.parse(c.getAttribute('data-filters')||'{}');
            const tags = Array.isArray(f.goal_tags) ? f.goal_tags : [];
            tags.forEach(t=>{ if(!panelAllTags.includes(t)) panelAllTags.push(t); });
          });
          // Preserve existing non-panel goal tags
          const existing = Array.isArray(searchState.__presetFilters?.goal_tags) ? searchState.__presetFilters.goal_tags : [];
          const preserved = existing.filter(t => !panelAllTags.includes(t));
          const newGoalTags = Array.from(new Set([ ...preserved, ...selectedPanelTags ]));
          const merged = { ...(searchState.__presetFilters||{}), in_stock: true };
          if (newGoalTags.length > 0) merged.goal_tags = newGoalTags; else delete merged.goal_tags;
          searchState.__presetFilters = merged;
          searchState.activePreset = newGoalTags.length === 1 && selectedPanelTags.length === 1 && preserved.length === 0 ? (selectedPanelLabels[0] || null) : null;
          searchState.activeGoals = selectedPanelLabels;
          updateAppliedBar();
          if (window.checkChipOverflow) window.checkChipOverflow();
          runFilteredSearch();
          return;
        }
        const filters = JSON.parse(card.getAttribute('data-filters') || '{}');
        const isActive = card.classList.contains('active');
        // Toggle off if already active
        if (isActive) {
          clearPreset();
          const goalsModal = $('#goalsModal');
          const goalsOverlay = $('#goalsOverlay');
          if (goalsModal && goalsModal.style.display !== 'none') {
            goalsModal.style.display = 'none';
            goalsOverlay.style.display = 'none';
          }
          runFilteredSearch();
          return;
        }
        // Apply new preset
        clearPreset();
        card.classList.add('active');
        searchState.activePreset = card.querySelector('.preset-label').textContent;
        // Apply filters from preset (goal_tags only) and ensure in_stock=true
        const merged = { ...filters };
        merged.in_stock = true;
        // Turn off all chips first
        allChips.forEach(c=>{ c.setAttribute('aria-pressed','false'); searchState.activeFilters.delete(c); });
        // Try to turn on matching chips for goal tag and in_stock
        Object.entries(merged).forEach(([key, value]) => {
          allChips.forEach(chip => {
            const chipFilter = JSON.parse(chip.getAttribute('data-filter') || '{}');
            if (JSON.stringify(chipFilter) === JSON.stringify({ [key]: value })) {
              chip.setAttribute('aria-pressed', 'true');
              const label = chip.textContent.trim().replace(/\s+/g, ' ');
              searchState.activeFilters.set(chip, label);
            }
          });
        });
        // If some preset filters had no chips (e.g., goal_tags without explicit chip), keep them for request time
        searchState.__presetFilters = merged;
        updateAppliedBar();
        runFilteredSearch();
        const goalsModal = $('#goalsModal');
        const goalsOverlay = $('#goalsOverlay');
        if (goalsModal && goalsModal.style.display !== 'none') {
          goalsModal.style.display = 'none';
          goalsOverlay.style.display = 'none';
        }
      });
    });
  }
  
  setupPresetCards();
  // Shop by goal chips under search
  const goalChips = $$('#goalChips .chip[data-preset]');
  goalChips.forEach(ch => {
    if (ch.__presetChipBound) return; ch.__presetChipBound = true;
    ch.addEventListener('click', () => {
      const key = ch.getAttribute('data-preset');
      const card = $(`.preset-card[data-preset="${key}"]`);
      if (card) card.click();
    });
  });
  
  // Handle modal buttons
  const moreGoalsBtn = $('#moreGoalsBtn');
  const goalsModal = $('#goalsModal');
  const goalsOverlay = $('#goalsOverlay');
  const goalsClose = $('#goalsClose');
  const applyGoals = $('#applyGoals');
  const resetGoals = $('#resetGoals');
  
  function syncGoalsModalFromState(){
    // Clear all active marks first
    $$('#goalsModal .preset-card').forEach(c=>c.classList.remove('active'));
    // Prefer syncing by explicit selected goal labels to avoid accidental tag overlaps
    const selectedLabels = Array.isArray(searchState.activeGoals) ? searchState.activeGoals : [];
    if (selectedLabels.length > 0) {
      $$('#goalsModal .preset-card').forEach(card=>{
        const lbl = card.querySelector('.preset-label')?.textContent?.trim();
        if (lbl && selectedLabels.includes(lbl)) card.classList.add('active');
      });
      return;
    }
    // Fallback: sync by tags only if no labels are known
    const current = searchState.__presetFilters || {};
    const arr = Array.isArray(current.goal_tags) ? current.goal_tags : [];
    if (arr.length === 0) return;
    $$('#goalsModal .preset-card').forEach(card=>{
      const f = JSON.parse(card.getAttribute('data-filters')||'{}');
      const tags = Array.isArray(f.goal_tags) ? f.goal_tags : [];
      if (tags.some(t=>arr.includes(t))) card.classList.add('active');
    });
  }

  moreGoalsBtn?.addEventListener('click', () => {
    goalsModal.style.display = '';
    goalsOverlay.style.display = '';
    setupPresetCards(); // Re-setup for any new cards (base behavior)
    searchState.goalsMultiSelect = true;
    // In goals modal, clicking a card should toggle selection without immediate search
    $$('#goalsModal .preset-card').forEach(card => {
      if (card.__goalsToggleBound) return; card.__goalsToggleBound = true;
      card.addEventListener('click', (e) => {
        e.stopPropagation();
        card.classList.toggle('active');
      });
    });
    // Sync from current state
    syncGoalsModalFromState();
  });
  
  goalsClose?.addEventListener('click', (e) => {
    e.stopPropagation();
    goalsModal.style.display = 'none';
    goalsOverlay.style.display = 'none';
    searchState.goalsMultiSelect = false;
  });
  
  goalsOverlay?.addEventListener('click', (e) => {
    e.stopPropagation();
    goalsModal.style.display = 'none';
    goalsOverlay.style.display = 'none';
    searchState.goalsMultiSelect = false;
  });

  // Apply/Reset for goals modal (multi-select)
  applyGoals?.addEventListener('click', (e)=>{
    e.stopPropagation();
    // Gather selected goal tags from active cards
    const selected = [];
    const selectedLabels = [];
    $$('#goalsModal .preset-card.active').forEach(card=>{
      const f = JSON.parse(card.getAttribute('data-filters')||'{}');
      const tags = Array.isArray(f.goal_tags) ? f.goal_tags : [];
      tags.forEach(t=>{ if(!selected.includes(t)) selected.push(t); });
      const lbl = card.querySelector('.preset-label')?.textContent?.trim();
      if (lbl && !selectedLabels.includes(lbl)) selectedLabels.push(lbl);
    });
    // Build merged filters: always include in_stock=true
    const merged = { ...(searchState.__presetFilters||{}), in_stock: true };
    if (selected.length > 0) merged.goal_tags = selected; else delete merged.goal_tags;
    // Clear preset label; we are in custom multi-goal selection mode
    searchState.activePreset = selected.length === 1 ? (selectedLabels[0] || null) : null;
    searchState.activeGoals = selected.length > 1 ? selectedLabels : (selected.length === 1 ? [selectedLabels[0]] : []);
    // Turn off all chips (goal presets are not chips) but leave other chips as-is
    // We only modify __presetFilters to carry goal_tags and in_stock
    searchState.__presetFilters = merged;
    // Sync top-level panel goal cards to reflect selected goals
    $$('#searchPanel .preset-grid .preset-card').forEach(c => {
      const f = JSON.parse(c.getAttribute('data-filters')||'{}');
      const tags = Array.isArray(f.goal_tags) ? f.goal_tags : [];
      const on = tags.some(t => selected.includes(t));
      c.classList.toggle('active', on);
    });
    goalsModal.style.display = 'none';
    goalsOverlay.style.display = 'none';
    searchState.goalsMultiSelect = false;
    updateAppliedBar();
    if (window.checkChipOverflow) window.checkChipOverflow();
    runFilteredSearch();
  });

  resetGoals?.addEventListener('click', (e)=>{
    e.stopPropagation();
    // Clear goal selections in modal
    $$('#goalsModal .preset-card.active').forEach(card=>card.classList.remove('active'));
    // Do NOT modify main state here; reset is local to modal until Apply
    searchState.goalsMultiSelect = true; // stay in modal selection mode
  });
  
  const allFiltersBtn = $('#allFiltersBtn');
  const filtersModal = $('#filtersModal');
  const filtersClose = $('#filtersClose');
  const applyFilters = $('#applyFilters');
  const resetFilters = $('#resetFilters');
  
  allFiltersBtn?.addEventListener('click', () => {
    filtersModal.style.display = '';
    goalsOverlay.style.display = '';
    
    // Sync checkboxes with current chip states
    const inStockChip = $('.chip[data-filter*="in_stock"]');
    const inStockCheckbox = $('#filterInStock');
    if (inStockChip && inStockCheckbox) {
      inStockCheckbox.checked = inStockChip.getAttribute('aria-pressed') === 'true';
    }
    
    const veganChip = $('.chip[data-filter*="vegan"]:not([data-filter*="sugar_free"])');
    const veganCheckbox = $('#filterVegan');
    if (veganChip && veganCheckbox) {
      veganCheckbox.checked = veganChip.getAttribute('aria-pressed') === 'true';
    }
    
    const glutenFreeCheckbox = $('#filterGlutenFree');
    const glutenFreeChip = $('.chip[data-filter*="gluten_free"]');
    if (glutenFreeChip && glutenFreeCheckbox) {
      glutenFreeCheckbox.checked = glutenFreeChip.getAttribute('aria-pressed') === 'true';
    }
    
    const sugarFreeCheckbox = $('#filterSugarFree');
    const sugarFreeChip = $('.chip[data-filter*="sugar_free"]');
    if (sugarFreeChip && sugarFreeCheckbox) {
      sugarFreeCheckbox.checked = sugarFreeChip.getAttribute('aria-pressed') === 'true';
    }
    
    const lactoseFreeCheckbox = $('#filterLactoseFree');
    const lactoseFreeChip = $('.chip[data-filter*="lactose_free"]');
    if (lactoseFreeChip && lactoseFreeCheckbox) {
      lactoseFreeCheckbox.checked = lactoseFreeChip.getAttribute('aria-pressed') === 'true';
    }
  });
  
  filtersClose?.addEventListener('click', (e) => {
    e.stopPropagation();
    filtersModal.style.display = 'none';
    goalsOverlay.style.display = 'none';
  });
  
  // Handle checkbox filters in modal
  function updateCheckboxFilters() {
    const inStockCheckbox = $('#filterInStock');
    const veganCheckbox = $('#filterVegan');
    const glutenFreeCheckbox = $('#filterGlutenFree');
    const sugarFreeCheckbox = $('#filterSugarFree');
    const lactoseFreeCheckbox = $('#filterLactoseFree');
    
    // In stock
    const inStockChip = $('.chip[data-filter*="in_stock"]');
    if (inStockChip && inStockCheckbox) {
      if (inStockCheckbox.checked && inStockChip.getAttribute('aria-pressed') !== 'true') {
        updateChipSelection(inStockChip);
      } else if (!inStockCheckbox.checked && inStockChip.getAttribute('aria-pressed') === 'true') {
        updateChipSelection(inStockChip);
      }
    }
    
    // Diet checkboxes
    const dietCheckboxes = [
      { checkbox: veganCheckbox, filter: 'vegan' },
      { checkbox: glutenFreeCheckbox, filter: 'gluten_free' },
      { checkbox: sugarFreeCheckbox, filter: 'sugar_free' },
      { checkbox: lactoseFreeCheckbox, filter: 'lactose_free' }
    ];
    
    dietCheckboxes.forEach(({ checkbox, filter }) => {
      if (!checkbox) return;
      const chip = $(`.chip[data-filter*="${filter}"]`);
      if (chip) {
        if (checkbox.checked && chip.getAttribute('aria-pressed') !== 'true') {
          updateChipSelection(chip);
        } else if (!checkbox.checked && chip.getAttribute('aria-pressed') === 'true') {
          updateChipSelection(chip);
        }
      }
    });
  }
  
  applyFilters?.addEventListener('click', (e) => {
    e.stopPropagation();
    // Apply filter changes from modal
    updateCheckboxFilters();
    filtersModal.style.display = 'none';
    goalsOverlay.style.display = 'none';
    updateAppliedBar();
    if (window.checkChipOverflow) window.checkChipOverflow();
    runFilteredSearch();
  });
  
  resetFilters?.addEventListener('click', () => {
    // Reset all filters in modal
    clearPreset();
    searchState.activeFilters.clear();
    $$('#filtersModal .chip[role="switch"]').forEach(chip => chip.setAttribute('aria-pressed', 'false'));
    $$('#filtersModal input[type="checkbox"]').forEach(cb => cb.checked = false);
    $('#filterInStock').checked = true;
    updateAppliedBar();
  });

  // Toggle chips + run search
  function getActiveFilters(){
    const groups = new Map();
    allChips.forEach(chip=>{
      if(chip.getAttribute('aria-pressed')==='true'){
        try{
          const group = chip.getAttribute('data-filter-group')||chip.textContent.trim();
          const f = JSON.parse(chip.getAttribute('data-filter')||'{}');
          const prev = groups.get(group) || {};
          // Merge with OR semantics for array fields, override for scalar/comparison
          for(const [k,v] of Object.entries(f)){
            if(Array.isArray(v)){
              const arr = Array.isArray(prev[k]) ? prev[k] : [];
              groups.set(group, { ...prev, [k]: Array.from(new Set(arr.concat(v))) });
            }else{
              groups.set(group, { ...prev, [k]: v });
            }
          }
          if(Object.keys(f).length===0) groups.set(group, prev);
        }catch(_e){ /* ignore */ }
      }
    });
    // Flatten groups into a single filter object; later groups can override same scalar fields
    const out = {};
    for(const obj of groups.values()){
      for(const [k,v] of Object.entries(obj)){
        if(Array.isArray(v)){
          const arr = Array.isArray(out[k]) ? out[k] : [];
          out[k] = Array.from(new Set(arr.concat(v)));
        }else{
          out[k] = v;
        }
      }
    }
    return out;
  }

  function updateChipSelection(chip){
    const group = chip.getAttribute('data-filter-group');
    const isOn = chip.getAttribute('aria-pressed')==='true';
    const label = chip.textContent.trim().replace(/\s+/g, ' ');
    
    if(group){
      // Single-select per group: turn others off if enabling this one
      if(!isOn){
        allChips.forEach(c=>{ 
          if(c!==chip && c.getAttribute('data-filter-group')===group){ 
            c.setAttribute('aria-pressed','false');
            searchState.activeFilters.delete(c);
          } 
        });
      }
    }
    
    chip.setAttribute('aria-pressed', String(!isOn));
    
    // Update active filters
    if (!isOn) {
      searchState.activeFilters.set(chip, label);
    } else {
      searchState.activeFilters.delete(chip);
    }
    
    // Clear preset if manually changing filters
    if (searchState.activePreset) {
      searchState.activePreset = null;
      $$('.preset-card').forEach(card => card.classList.remove('active'));
    }
    
    updateAppliedBar();
    if (window.checkChipOverflow) window.checkChipOverflow();
    markChangedSinceApply();
    persistQuickFilters();
  }

  async function performSearch({ append=false }={}){
    if(!productsList || oState.loading) return;
    const query = (overlayInput?.value||headerInput?.value||'').trim();
    const filters = { ...getActiveFilters(), ...(searchState.__presetFilters||{}) };
    if (!append) {
      oState.page = 1; oState.reachedEnd = false; productsList.innerHTML = '<div class="muted" style="padding:8px">Searching…</div>';
    }
    try{
      oState.loading = true;
      const sort = (() => {
        switch (searchState.sortOrder) {
          case 'price_asc': return ['price_cents:asc'];
          case 'price_desc': return ['price_cents:desc'];
          case 'popular': return ['rating:desc', 'review_count:desc'];
          case 'relevance':
          case 'new':
          default: return undefined;
        }
      })();
      const searchParams = { 
        page: oState.page,
        size: oState.size, 
        filters,
        ...(sort ? { sort } : {})
      };
      const data = await searchProducts(query, searchParams);
      const items = Array.isArray(data?.items) ? data.items : [];
      const total = typeof data?.total === 'number' ? data.total : (items||[]).length;
      updateResultCount(total);
      const html = items.length ? items.map(it=>renderProductHTML(it, query)).join('') : '<div class="muted" style="padding:8px">No results</div>';
      if (append && items.length) productsList.insertAdjacentHTML('beforeend', html); else productsList.innerHTML = html;
      attachAddHandlers(productsList);
      attachOpenHandlers(productsList);
      if (items.length < oState.size) oState.reachedEnd = true;
      setupOverlayInfinite();
      // Update footer CTA label
      try{
        const btn = document.getElementById('applyFooterBtn');
        if (btn) {
          const count = total;
          btn.textContent = count > 0 ? `Show results (${count})` : 'Show results';
          btn.disabled = count === 0 && !append;
        }
      }catch(_e){}
      // URL update unless on PDP
      if (!String(location.hash||'').startsWith('#/p/')){
        const path = buildSearchPath(query, filters);
        const current = location.hash.slice(1) || '';
        if (current !== path) navigate(path);
      }
      if (query) persistRecentQuery(query);
    }catch(e){
      console.error('Search error:', e);
      if (!append) productsList.innerHTML = '<div class="muted" style="padding:8px">Search failed. Check API.</div>';
    } finally {
      oState.loading = false;
    }
  }
  // Preserve compatibility
  async function runFilteredSearch(){ return performSearch({ append:false }); }
  // Expose for components outside this closure (e.g., applied filters bar)
  window.__runFilteredSearch = runFilteredSearch;

  // Set up chip click handlers for all chips (including modal chips)
  function setupChipHandlers() {
    const allChipsEverywhere = $$('.chip[role="switch"]');
    allChipsEverywhere.forEach(chip => {
      if (chip.__hasChipHandler) return;
      chip.__hasChipHandler = true;
      chip.addEventListener('click', () => { 
        updateChipSelection(chip); 
        runFilteredSearch(); 
      });
    });
  }
  
  setupChipHandlers();
  
  // Re-setup when modals open to catch any new chips
  allFiltersBtn?.addEventListener('click', () => {
    setTimeout(setupChipHandlers, 10);
  });

  // Contextual defaults based on query
  function applyContextualDefaults(query) {
    const q = query.toLowerCase();
    
    // Clear previous defaults
    $$('.preset-card').forEach(card => card.classList.remove('suggested'));
    
    // Suggest presets based on query
    if (q.includes('pre') || q.includes('workout') || q.includes('energy')) {
      const preWorkoutCard = $('.preset-card[data-preset="preworkout"]');
      if (preWorkoutCard) {
        preWorkoutCard.classList.add('suggested');
      }
    }
    
    if (q.includes('protein') || q.includes('whey') || q.includes('build')) {
      const strengthCard = $('.preset-card[data-preset="strength"]');
      if (strengthCard) {
        strengthCard.classList.add('suggested');
      }
    }
    
    if (q.includes('creatine') || q.includes('strength')) {
      const strengthCard = $('.preset-card[data-preset="strength"]');
      if (strengthCard) {
        strengthCard.classList.add('suggested');
      }
      // Auto-apply price filter for budget products
      if (q.includes('budget') || q.includes('cheap')) {
        const priceChip = $('.chip[data-filter*="2000"]');
        if (priceChip && priceChip.getAttribute('aria-pressed') !== 'true') {
          updateChipSelection(priceChip);
        }
      }
    }
    
    if (q.includes('vitamin') || q.includes('wellness') || q.includes('health')) {
      const wellnessCard = $('.preset-card[data-preset="wellness"]');
      if (wellnessCard) {
        wellnessCard.classList.add('suggested');
      }
    }
  }

  // Live search
  const debounce = (fn, d=350)=>{ let t; return (...a)=>{ clearTimeout(t); t=setTimeout(()=>fn(...a), d); }; };
  async function runSearch(q){
    if(!productsList) return;
    const query = (q||'').trim();
    
    // Apply contextual defaults
    if (query.length >= 3) {
      applyContextualDefaults(query);
    }
    
    // When filters are active, allow empty query
    const filtersActive = Object.keys(getActiveFilters()).length>0;
    if(query.length < 2 && !filtersActive) return;
    return performSearch({ append:false });
  }
  const debounced = debounce(runSearch, 300);
  overlayInput?.addEventListener('input', (e)=>{
    const val = e.target.value || '';
    const query = val.trim();
    const presetActive = !!(searchState.__presetFilters && Object.keys(searchState.__presetFilters||{}).length>0);
    const filtersActive = Object.keys(getActiveFilters()).length>0 || presetActive;
    if (query.length === 0 && !filtersActive) {
      // Clear seeded/static results when overlay opens with no state
      if (productsList) productsList.innerHTML = '';
      renderPreInputSuggestions();
    }
    debounced(val);
  });
  overlayInput?.addEventListener('keydown', (e)=>{ 
    if(e.key==='Enter'){
      e.preventDefault();
      const query = (overlayInput?.value||'').trim();
      const presetActive = !!(searchState.__presetFilters && Object.keys(searchState.__presetFilters||{}).length>0);
      const filtersActive = Object.keys(getActiveFilters()).length>0 || presetActive;
      if (query.length === 0 && !filtersActive) return;
      runSearch(overlayInput.value);
      if (query) persistRecentQuery(query);
    } 
  });

  // Keep header input in sync: typing in header should open overlay and mirror value
  headerInput?.addEventListener('input', ()=>{
    if (!searchPanel?.classList.contains('visible')) openOverlay();
    if (overlayInput) overlayInput.value = headerInput.value || '';
    overlayInput?.dispatchEvent(new Event('input'));
  });

  // Initial wiring for static Add buttons
  attachAddHandlers(document);

  // If any chip is preselected, run search immediately ONLY on PDP
  if(isOnPdp() && Array.from(allChips).some(c=>c.getAttribute('aria-pressed')==='true')){
    // Initialize activeFilters for pre-selected chips
    allChips.forEach(chip => {
      if (chip.getAttribute('aria-pressed') === 'true') {
        const label = chip.textContent.trim().replace(/\s+/g, ' ');
        searchState.activeFilters.set(chip, label);
      }
    });
    updateAppliedBar();
    runFilteredSearch();
  }

  // Re-fetch results and refresh labels when language changes
  bus.addEventListener('language:changed', () => {
    // Rebuild applied bar labels from current chip texts
    const updated = new Map();
    searchState.activeFilters.forEach((_label, chip) => {
      const label = chip.textContent.trim().replace(/\s+/g, ' ');
      updated.set(chip, label);
    });
    searchState.activeFilters = updated;
    updateAppliedBar();
    if (window.checkChipOverflow) window.checkChipOverflow();
    if (window.__runFilteredSearch) window.__runFilteredSearch();
  });

  // Initial restore from persisted chips and show suggestions
  restorePersisted();
  renderPreInputSuggestions();

  // Deep link: open overlay when hash is /search or URL has ?open=1
  try{
    const openParam = new URLSearchParams(location.search).get('open');
    const inHash = (location.hash||'').startsWith('#/search');
    if (openParam === '1' || inHash) {
      openOverlay();
      const hqs = parseHashQuery();
      const q = hqs.get('q') || '';
      if (overlayInput) { overlayInput.value = q; }
      applyFiltersFromParams(hqs);
      const hasFilters = Object.keys(getActiveFilters()).length>0 || (searchState.__presetFilters && Object.keys(searchState.__presetFilters).length>0);
      if (q || hasFilters) window.__runFilteredSearch?.(); else renderPreInputSuggestions();
    }
  }catch(_e){ /* ignore */ }

  // Keep overlay in sync when /search hash changes
  window.addEventListener('hashchange', ()=>{
    const h = String(location.hash||'');
    if (h.startsWith('#/search')){
      if (!searchPanel?.classList.contains('visible')) openOverlay();
      const hqs = parseHashQuery();
      const q = hqs.get('q') || '';
      if (overlayInput) overlayInput.value = q;
      applyFiltersFromParams(hqs);
      const hasFilters = Object.keys(getActiveFilters()).length>0 || (searchState.__presetFilters && Object.keys(searchState.__presetFilters).length>0);
      if (q || hasFilters) window.__runFilteredSearch?.(); else renderPreInputSuggestions();
    }
  });

  // Footer Apply: expand to full page (/search) and close overlay
  applyFooterBtn?.addEventListener('click', ()=>{
    const query = (overlayInput?.value||headerInput?.value||'').trim();
    const filters = { ...getActiveFilters(), ...(searchState.__presetFilters||{}) };
    const path = buildSearchPath(query, filters);
    const current = location.hash.slice(1) || '';
    if (current !== path) navigate(path);
    closeOverlay();
  });
}

// Apply filters from URLSearchParams to chips/state
function applyFiltersFromParams(params){
  try{
    const target = {};
    params.forEach((val, key)=>{
      if (!val) return;
      switch (key) {
        case 'in_stock':
          target.in_stock = (val === '1' || val === 'true');
          break;
        case 'form':
          target.form = val;
          break;
        case 'goal_tags': {
          const arr = val.split(',').map(s=>s.trim()).filter(Boolean);
          if (arr.length) target.goal_tags = arr;
          break;
        }
        case 'diet_tags': {
          const arr = val.split(',').map(s=>s.trim()).filter(Boolean);
          if (arr.length) target.diet_tags = arr;
          break;
        }
        case 'price_cents': {
          const m = decodeURIComponent(val).match(/^(<=|>=|<|>|=|eq|lte|gte):?(\d+)$/i);
          if (m) target.price_cents = { op: m[1].toLowerCase().replace('eq','=').replace('lte','<=').replace('gte','>='), value: Number(m[2]) };
          break;
        }
        case 'price_per_serving': {
          const m = decodeURIComponent(val).match(/^(<=|>=|<|>|=|eq|lte|gte):?(\d+(?:\.\d+)?)$/i);
          if (m) target.price_per_serving = { op: m[1].toLowerCase().replace('eq','=').replace('lte','<=').replace('gte','>='), value: Number(m[2]) };
          break;
        }
        default:
          target[key] = val;
      }
    });

    // Reset
    $$('.chip[role="switch"]').forEach(chip => chip.setAttribute('aria-pressed', 'false'));
    searchState.activeFilters.clear();
    searchState.__presetFilters = {};

    const toggleByFilter = (obj)=>{
      const labelCache = new Map();
      $$('.chip[role="switch"]').forEach(chip=>{
        const f = getChipFilterObject(chip);
        if (areFiltersEqual(f, obj)){
          chip.setAttribute('aria-pressed','true');
          const label = chip.textContent.trim().replace(/\s+/g, ' ');
          labelCache.set(chip, label);
        }
      });
      labelCache.forEach((label, chip)=> searchState.activeFilters.set(chip, label));
      return labelCache.size > 0;
    };

    if (typeof target.in_stock === 'boolean'){
      if (!toggleByFilter({ in_stock: true })) searchState.__presetFilters.in_stock = target.in_stock;
    }
    if (typeof target.form === 'string' && target.form){
      if (!toggleByFilter({ form: target.form })) searchState.__presetFilters.form = target.form;
    }
    if (Array.isArray(target.goal_tags)){
      // No explicit chips exist for individual goal tags; store in preset filters
      const uniq = Array.from(new Set(target.goal_tags));
      searchState.__presetFilters.goal_tags = uniq;
      // Try to reflect selection on top-level goal cards
      $$('#searchPanel .preset-grid .preset-card').forEach(c => {
        const f = JSON.parse(c.getAttribute('data-filters')||'{}');
        const tags = Array.isArray(f.goal_tags) ? f.goal_tags : [];
        const on = tags.some(t => uniq.includes(t));
        c.classList.toggle('active', on);
      });
      // Update appliedGoals chip label (multi-select summary)
      const labels = [];
      $$('#searchPanel .preset-grid .preset-card').forEach(c => {
        const f = JSON.parse(c.getAttribute('data-filters')||'{}');
        const tags = Array.isArray(f.goal_tags) ? f.goal_tags : [];
        if (tags.some(t => uniq.includes(t))) {
          const lbl = c.querySelector('.preset-label')?.textContent?.trim();
          if (lbl && !labels.includes(lbl)) labels.push(lbl);
        }
      });
      searchState.activePreset = labels.length === 1 ? labels[0] : null;
      searchState.activeGoals = labels.length > 1 ? labels : (labels.length === 1 ? [labels[0]] : []);
    }
    if (Array.isArray(target.diet_tags)){
      const remaining = new Set(target.diet_tags);
      ['vegan','gluten_free','sugar_free','lactose_free'].forEach(tag=>{
        if (remaining.has(tag)){
          toggleByFilter({ diet_tags: [tag] });
          remaining.delete(tag);
        }
      });
      if (remaining.size > 0) searchState.__presetFilters.diet_tags = Array.from(remaining);
    }
    if (target.price_cents && typeof target.price_cents === 'object'){
      if (!toggleByFilter({ price_cents: target.price_cents })) searchState.__presetFilters.price_cents = target.price_cents;
    }
    if (target.price_per_serving && typeof target.price_per_serving === 'object'){
      if (!toggleByFilter({ price_per_serving: target.price_per_serving })) searchState.__presetFilters.price_per_serving = target.price_per_serving;
    }

    updateAppliedBar();
    if (window.checkChipOverflow) window.checkChipOverflow();
  }catch(_e){ /* ignore */ }
}


