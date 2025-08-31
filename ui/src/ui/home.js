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
  preset: 'bestsellers',
  loading: false,
  reachedEnd: false
};

function sortToBackend(sortKey){
  switch (sortKey) {
    case 'price_asc': return ['price_cents:asc'];
    case 'price_desc': return ['price_cents:desc'];
    case 'popular': return ['rating:desc', 'review_count:desc'];
    case 'new': return undefined; // let backend decide (fallback)
    case 'rank':
    default: return undefined; // backend default / merchandising
  }
}

function presetToFilters(key){
  switch (key) {
    case 'wellness': return { in_stock: true, goal_tags: ['wellness'] };
    case 'trending': return { in_stock: true };
    case 'new': return { in_stock: true };
    case 'bestsellers':
    default: return { in_stock: true };
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
    else sub.push('New');
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
          <button class="add js-add" data-name="${name}" data-flavors='${JSON.stringify(flavorsList)}'>${btnLabel}</button>
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
      return;
    }
    const html = items.map(renderCardHTML).join('');
    if (append) grid.insertAdjacentHTML('beforeend', html); else grid.innerHTML = html;
    bindHandlers(grid);
    if (items.length < state.size) state.reachedEnd = true;
  } catch(err){
    console.warn('Home fetch failed', err);
    if (!append) grid.innerHTML = '<div class="muted" style="padding:8px">Failed to load. Check API.</div>';
  } finally {
    loader.remove();
    state.loading = false;
  }
}

export function mountHome(){
  const section = $('#home');
  const sortSel = $('#homeSort');
  const moreBtn = $('#homeMore');

  // Preset chips
  $$('#home .chip[data-home-preset]').forEach(ch=>{
    if (ch.__presetBound) return; ch.__presetBound = true;
    ch.addEventListener('click', ()=>{
      $$('#home .chip[data-home-preset]').forEach(x=>x.setAttribute('aria-pressed','false'));
      ch.setAttribute('aria-pressed','true');
      state.preset = ch.getAttribute('data-home-preset');
      state.page = 1; state.reachedEnd = false;
      fetchPage({ append:false });
    });
  });

  // Default active preset
  const def = $('#home .chip[data-home-preset="bestsellers"]');
  if (def) def.setAttribute('aria-pressed','true');

  // Sort
  sortSel?.addEventListener('change', (e)=>{
    state.sort = e.target.value;
    state.page = 1; state.reachedEnd = false;
    fetchPage({ append:false });
  });

  // Load more
  moreBtn?.addEventListener('click', ()=>{
    if (state.loading || state.reachedEnd) return;
    state.page += 1;
    fetchPage({ append:true });
  });

  // Initial load
  if (section) fetchPage({ append:false });

  // Re-fetch current page when language changes
  bus.addEventListener('language:changed', () => {
    state.page = 1; state.reachedEnd = false;
    fetchPage({ append:false });
  });
}

export function showHome(){ const s = $('#home'); if (s) s.classList.add('visible'); }
export function hideHome(){ const s = $('#home'); if (s) s.classList.remove('visible'); }


