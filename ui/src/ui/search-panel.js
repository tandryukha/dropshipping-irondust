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


