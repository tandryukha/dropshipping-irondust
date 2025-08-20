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
function formatEuro(value, currency='EUR'){
  if(typeof value !== 'number' || isNaN(value)) return '';
  const symbol = currency === 'EUR' ? '€' : '';
  return symbol + value.toFixed(2);
}
function extractFlavors(dynamicAttrs){
  try{
    const obj = dynamicAttrs || {};
    const k = Object.keys(obj).find(x=>/flav|taste|maitse/i.test(x));
    if(k && Array.isArray(obj[k])) return obj[k];
  }catch(_){ }
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
    pdpFacts: document.querySelector('.facts'),
    qaChips: document.querySelector('.qa-chips'),
    ans1: $('#ans1'),
    ans2: $('#ans2'),
    ans3: $('#ans3'),
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
      // Description prefers enriched benefit_snippet, falls back to search_text
      const desc = (typeof prod?.benefit_snippet === 'string' && prod.benefit_snippet)
        ? prod.benefit_snippet
        : (typeof prod?.search_text === 'string' ? prod.search_text.slice(0, 320) : '');
      if (els.pdpDescText) els.pdpDescText.textContent = desc;

      // Flavors: prefer explicit flavor, else parse from dynamic attributes
      const flavors = (()=>{
        const fromAttrs = extractFlavors(prod?.dynamic_attrs);
        if (Array.isArray(fromAttrs) && fromAttrs.length) return fromAttrs;
        if (typeof prod?.flavor === 'string' && prod.flavor.trim()) return [prod.flavor.trim()];
        return [];
      })();
      renderPdpFlavors(flavors);
      if (!flavors.length && typeof prod?.flavor === 'string') {
        els.pdpFlavorLabel.textContent = prod.flavor;
      }

      // Facts: render enriched fields if available
      if (els.pdpFacts) {
        const facts = [];
        const form = typeof prod?.form === 'string' ? prod.form : '';
        const isCountBased = form === 'capsules' || form === 'tabs';
        if (form) facts.push(`Form: ${form}`);
        if (!isCountBased) {
          if (typeof prod?.net_weight_g === 'number') facts.push(`Net: ${prod.net_weight_g} g`);
          if (typeof prod?.serving_size_g === 'number') facts.push(`Serving size: ${prod.serving_size_g} g`);
          if (typeof prod?.servings === 'number') {
            facts.push(`Servings: ${prod.servings}`);
          } else if (typeof prod?.servings_min === 'number' && typeof prod?.servings_max === 'number') {
            facts.push(`Servings: ${prod.servings_min}–${prod.servings_max}`);
          }
          if (typeof prod?.price_per_serving === 'number') facts.push(`Per serving: ${formatEuro(prod.price_per_serving, prod?.currency||'EUR')}`);
          if (typeof prod?.price_per_100g === 'number') facts.push(`Per 100g: ${formatEuro(prod.price_per_100g, prod?.currency||'EUR')}`);
        } else {
          if (typeof prod?.unit_count === 'number') facts.push(`Units: ${prod.unit_count}`);
          if (typeof prod?.units_per_serving === 'number') facts.push(`Dose: ${prod.units_per_serving} ${form === 'tabs' ? 'tabs' : 'caps'}`);
          if (typeof prod?.unit_mass_g === 'number') facts.push(`Per ${form === 'tabs' ? 'tab' : 'cap'}: ${Math.round(prod.unit_mass_g*1000)} mg`);
          if (typeof prod?.price_per_unit === 'number') facts.push(`Per unit: ${formatEuro(prod.price_per_unit, prod?.currency||'EUR')}`);
          // If units_per_serving present, compute servings; else prefer not to show derived servings to avoid confusion
          if (typeof prod?.units_per_serving === 'number' && prod.units_per_serving > 0 && typeof prod?.unit_count === 'number') {
            const srv = Math.floor(prod.unit_count / prod.units_per_serving);
            if (srv > 0) facts.push(`Servings: ${srv}`);
          }
        }
        if (Array.isArray(prod?.goal_tags)) prod.goal_tags.forEach(t=>{ if(typeof t==='string'&&t) facts.push(t); });
        if (Array.isArray(prod?.diet_tags)) prod.diet_tags.forEach(t=>{ if(typeof t==='string'&&t) facts.push(t); });
        els.pdpFacts.innerHTML = facts.map(x=>`<span class="fact">${x}</span>`).join('');
      }

      // FAQ: map first three items to existing chips/answers
      if (Array.isArray(prod?.faq) && prod.faq.length) {
        const faqs = prod.faq.slice(0,3);
        if (els.qaChips) {
          els.qaChips.innerHTML = faqs.map((f, i)=>`<button class="qa-chip" data-target="#ans${i+1}">${(f?.q||'Question')}</button>`).join('');
        }
        const ansEls = [els.ans1, els.ans2, els.ans3];
        faqs.forEach((f, i)=>{
          if (!ansEls[i]) return;
          ansEls[i].innerHTML = '';
          const wrap = document.createElement('div');
          wrap.textContent = f?.a || '';
          ansEls[i].appendChild(wrap);
        });
      }
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


