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
  const form = (typeof item?.form === 'string' ? item.form : '');
  if (form) subtitleBits.push(form);
  const isCountBased = form === 'capsules' || form === 'tabs';
  if (isCountBased) {
    if (typeof item?.unit_count === 'number') subtitleBits.push(`${item.unit_count} pcs`);
    if (typeof item?.price_per_unit === 'number') subtitleBits.push(`${symbol}${item.price_per_unit.toFixed(2)}/unit`);
  } else {
    if (typeof item?.serving_size_g === 'number') subtitleBits.push(`${item.serving_size_g} g/serv`);
    if (typeof item?.price_per_serving === 'number') subtitleBits.push(`€${item.price_per_serving.toFixed(2)}/serv`);
  }
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
        <button class="add js-add" data-name="${name}" data-flavors='${JSON.stringify((()=>{ const list=[]; if(typeof item?.flavor === "string" && item.flavor.trim()) list.push(item.flavor.trim()); return list; })())}'>Add</button>
      </div>
    </div>`;
}

export function mountSearchPanel() {
  const searchInput = $('#search');
  const searchPanel = $('#searchPanel');
  const productsList = $('#productsList');
  const allChips = $$('.chip');
  // Expand/collapse inline trays (search panel only)
  document.addEventListener('click', (e)=>{
    if(!e.target.closest('#searchPanel')) return;
    const btn = e.target.closest('[data-toggle]');
    if(!btn) return;
    const sel = btn.getAttribute('data-toggle');
    const el = sel ? document.querySelector(sel) : null;
    if(!el) return;
    const hidden = el.hasAttribute('hidden');
    if(hidden) el.removeAttribute('hidden'); else el.setAttribute('hidden','');
  });

  // Focus show/hide
  searchInput?.addEventListener('focus', ()=>searchPanel?.classList.add('visible'));
  document.addEventListener('click', (e)=>{
    const inside = e.target.closest('.search-wrap') || e.target.closest('#searchPanel');
    if(!inside) searchPanel?.classList.remove('visible');
  });
  // Remove old menu behavior


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
    if(group){
      // Single-select per group: turn others off if enabling this one
      if(!isOn){
        allChips.forEach(c=>{ if(c!==chip && c.getAttribute('data-filter-group')===group){ c.setAttribute('aria-pressed','false'); } });
      }
    }
    chip.setAttribute('aria-pressed', String(!isOn));
  }

  async function runFilteredSearch(){
    if(!productsList) return;
    const query = (searchInput?.value||'').trim();
    const filters = getActiveFilters();
    productsList.innerHTML = '<div class="muted" style="padding:8px">Searching…</div>';
    try{
      const data = await searchProducts(query, { size: 6, filters });
      const items = Array.isArray(data?.items) ? data.items : [];
      if(items.length === 0){ productsList.innerHTML = '<div class="muted" style="padding:8px">No results</div>'; return; }
      productsList.innerHTML = items.map(renderProductHTML).join('');
      attachAddHandlers(productsList);
      attachOpenHandlers(productsList);
    }catch(e){
      productsList.innerHTML = '<div class="muted" style="padding:8px">Search failed. Check API.</div>';
    }
  }

  allChips.forEach(chip=>{
    chip.addEventListener('click', ()=>{ updateChipSelection(chip); runFilteredSearch(); });
  });
  // Also attach for chips added in expanded trays (delegated)
  document.addEventListener('click', (e)=>{
    if(!e.target.closest('#searchPanel')) return;
    const chip = e.target.closest('.more-chips .chip[role="switch"]');
    if(chip){ updateChipSelection(chip); runFilteredSearch(); }
  });

  // Live search
  const debounce = (fn, d=350)=>{ let t; return (...a)=>{ clearTimeout(t); t=setTimeout(()=>fn(...a), d); }; };
  async function runSearch(q){
    if(!productsList) return;
    const query = (q||'').trim();
    // When filters are active, allow empty query
    const filtersActive = Object.keys(getActiveFilters()).length>0;
    if(query.length < 2 && !filtersActive) return;
    productsList.innerHTML = '<div class="muted" style="padding:8px">Searching…</div>';
    try{
      const data = await searchProducts(query, { size: 6, filters: getActiveFilters() });
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

  // If any chip is preselected, run search immediately
  if(Array.from(allChips).some(c=>c.getAttribute('aria-pressed')==='true')){
    runFilteredSearch();
  }
}


