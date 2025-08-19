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

  // Bind existing static flavor buttons if present
  if (els.pdpFlavors) {
    bindFlavorButtons(els.pdpFlavors);
    const preselected = els.pdpFlavors.querySelector('.flavor[aria-pressed="true"]');
    if (preselected && els.pdpFlavorLabel) {
      els.pdpFlavorLabel.textContent = preselected.dataset.flavor || preselected.textContent || '';
    }
  }

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


