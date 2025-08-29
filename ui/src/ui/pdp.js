import { $, $$ } from '../core/dom.js';
import { bus } from '../core/bus.js';
import { store } from '../core/store.js';
import { getProduct } from '../api/api.js';
import { openFor } from './flavor-popover.js';

const els = {};

// Helper function to extract dosage information from search_text
function extractDosageFromText(searchText) {
  if (!searchText) return null;
  
  // Look for patterns like "1 portsjon", "Soovitatav päevane annus:", etc.
  const dosagePatterns = [
    /Soovitatav päevane annus:\s*([^.]+)/i,
    /Päevane tarbimine:\s*([^.]+)/i,
    /(\d+\s*portsjon[^.]*)/i,
    /(\d+\s*mõõtelusikas[^.]*)/i,
    /(\d+[-–]\d+\s*(?:scoops?|servings?|portions?)[^.]*)/i
  ];
  
  for (const pattern of dosagePatterns) {
    const match = searchText.match(pattern);
    if (match) {
      return match[1].trim();
    }
  }
  return null;
}

// Helper function to extract timing information from search_text
function extractTimingFromText(searchText) {
  if (!searchText) return null;
  
  // Look for patterns related to timing
  const timingPatterns = [
    /(\d+[-–]\d+\s*minutit\s*enne\s*treeningut)/i,
    /(enne\s*treeningut[^.]*)/i,
    /(pärast\s*treeningut[^.]*)/i,
    /(post[-\s]?workout[^.]*)/i,
    /(pre[-\s]?workout[^.]*)/i,
    /(before\s*training[^.]*)/i,
    /(after\s*training[^.]*)/i
  ];
  
  for (const pattern of timingPatterns) {
    const match = searchText.match(pattern);
    if (match) {
      return match[0].trim();
    }
  }
  return null;
}

// Helper function to generate composition table from product data
function generateCompositionTable(prod) {
  if (!prod) return '<p class="muted" style="font-size:14px;">No composition data available</p>';
  
  // Extract composition from search_text if available
  const searchText = prod.search_text || '';
  
  // Look for composition section in the text
  const compositionMatch = searchText.match(/Koostisosad[^:]*:([^.]+)/i);
  if (compositionMatch) {
    const ingredients = compositionMatch[1].trim();
    const ingredientsList = ingredients.split(/[,;]/).map(ing => ing.trim()).filter(ing => ing);
    
    let html = '<div style="margin-bottom:16px;">';
    html += '<h4 style="margin:0 0 8px;font-size:14px;">Ingredients</h4>';
    html += '<ul style="margin:0;padding-left:20px;">';
    ingredientsList.forEach(ing => {
      html += `<li style="margin:4px 0;">${ing}</li>`;
    });
    html += '</ul>';
    html += '</div>';
    
    // Add any nutritional info if available
    if (prod.serving_size_g || prod.net_weight_g) {
      html += '<div style="margin-top:16px;">';
      html += '<h4 style="margin:0 0 8px;font-size:14px;">Product Information</h4>';
      html += '<table style="width:100%;border-collapse:collapse;">';
      html += '<tbody>';
      if (prod.net_weight_g) {
        html += `<tr><td style="padding:8px;border:1px solid #e5e7eb;">Net weight</td><td style="padding:8px;border:1px solid #e5e7eb;">${prod.net_weight_g} g</td></tr>`;
      }
      if (prod.serving_size_g) {
        html += `<tr><td style="padding:8px;border:1px solid #e5e7eb;">Serving size</td><td style="padding:8px;border:1px solid #e5e7eb;">${prod.serving_size_g} g</td></tr>`;
      }
      if (prod.servings) {
        html += `<tr><td style="padding:8px;border:1px solid #e5e7eb;">Servings</td><td style="padding:8px;border:1px solid #e5e7eb;">${prod.servings}</td></tr>`;
      }
      html += '</tbody></table>';
      html += '</div>';
    }
    
    return html;
  }
  
  // Fallback to basic product info
  return '<p class="muted" style="font-size:14px;">Composition information not available. Please check product packaging for details.</p>';
}

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
    // Tabs elements
    tabDescBtn: $('#tab-desc-btn'),
    tabCompBtn: $('#tab-comp-btn'),
    tabRevBtn: $('#tab-rev-btn'),
    tabDesc: $('#tab-desc'),
    tabComp: $('#tab-comp'),
    tabRev: $('#tab-rev'),
    // Dosage and timing elements
    dosageBox: document.querySelector('.mini .box:first-child'),
    timingBox: document.querySelector('.mini .box:last-child'),
  });

  // Bind existing static flavor buttons if present
  if (els.pdpFlavors) {
    bindFlavorButtons(els.pdpFlavors);
    const preselected = els.pdpFlavors.querySelector('.flavor[aria-pressed="true"]');
    if (preselected && els.pdpFlavorLabel) {
      els.pdpFlavorLabel.textContent = preselected.dataset.flavor || preselected.textContent || '';
    }
  }

  // Bind tab functionality
  if (els.tabDescBtn && els.tabCompBtn && els.tabRevBtn) {
    const tabs = [els.tabDescBtn, els.tabCompBtn, els.tabRevBtn];
    const panels = [els.tabDesc, els.tabComp, els.tabRev];
    
    tabs.forEach((tab, index) => {
      tab.addEventListener('click', () => {
        // Update tab states
        tabs.forEach(t => t.setAttribute('aria-selected', 'false'));
        tab.setAttribute('aria-selected', 'true');
        
        // Update panel visibility
        panels.forEach(p => p.classList.remove('active'));
        panels[index].classList.add('active');
      });
    });
  }

  // Bind QA chip functionality (toggle on repeated click)
  document.addEventListener('click', (e) => {
    const chip = e.target.closest('.qa-chip');
    if (!chip) return;
    const target = chip.dataset.target;
    if (!target) return;
    const ansEl = document.querySelector(target);
    const isActive = chip.classList.contains('active');
    if (isActive) {
      // Toggle off if already active
      chip.classList.remove('active');
      if (ansEl) ansEl.style.display = 'none';
      return;
    }
    // Activate clicked chip and show its answer
    document.querySelectorAll('.qa-ans').forEach(ans => ans.style.display = 'none');
    document.querySelectorAll('.qa-chip').forEach(c => c.classList.remove('active'));
    if (ansEl) {
      ansEl.style.display = 'block';
      chip.classList.add('active');
    }
  });

  // PDP qty controls
  els.pdpQtyMinus?.addEventListener('click', ()=>{ store.set('pdpQty', Math.max(1, store.get('pdpQty')-1)); els.pdpQtyVal.textContent = String(store.get('pdpQty')); });
  els.pdpQtyPlus?.addEventListener('click', ()=>{ store.set('pdpQty', store.get('pdpQty')+1); els.pdpQtyVal.textContent = String(store.get('pdpQty')); });

  // PDP Add button opens popover with selected flavor + qty
  els.pdpAdd?.addEventListener('click', ()=>{
    const initialFlavor = els.pdpFlavorLabel?.textContent || null;
    openFor(els.pdpAdd, { initialFlavor, initialQty: store.get('pdpQty') });
  });
  
  // Store reference to current product for tabs
  store.set('currentProduct', null);

  // Listen to route changes (open product)
  bus.addEventListener('open-product', async (e)=>{
    const id = e.detail?.id;
    if(!id) return;
    try{
      const prod = await getProduct(id);
      store.set('currentProduct', prod); // Store for tab updates
      els.pdpProductName.textContent = prod?.name || 'Product';
      els.pdpPrice.textContent = formatPrice(prod?.price_cents, prod?.currency||'EUR');
      els.pdpStars.textContent = renderStars(typeof prod?.rating === 'number' ? prod.rating : undefined);
      els.pdpReviewCount.textContent = String(prod?.review_count ?? '0');
      if (Array.isArray(prod?.images) && prod.images.length) {
        els.pdpImage.src = prod.images[0];
        els.pdpImage.alt = prod?.name || 'Product image';
      }
      // Description: Use full search_text if available, which contains all product information
      const desc = typeof prod?.search_text === 'string' ? prod.search_text : '';
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
      // Remove goal tags from PDP facts per requirement
      // if (Array.isArray(prod?.goal_tags)) prod.goal_tags.forEach(t=>{ if(typeof t==='string'&&t) facts.push(t); });
      if (Array.isArray(prod?.diet_tags)) prod.diet_tags.forEach(t=>{ if(typeof t==='string'&&t) facts.push(t); });
      els.pdpFacts.innerHTML = facts.map(x=>`<span class="fact">${x}</span>`).join('');
    }

    // Update Dosage and Timing boxes dynamically
    if (els.dosageBox) {
      const dosageContent = extractDosageFromText(prod?.search_text) || '1–2 scoops daily with water or milk';
      els.dosageBox.innerHTML = '<div style="font-size:12px;color:var(--fp-muted)">Dosage</div>' + dosageContent;
    }
    if (els.timingBox) {
      const timingContent = extractTimingFromText(prod?.search_text) || 'Within 30 mins post-workout';
      els.timingBox.innerHTML = '<div style="font-size:12px;color:var(--fp-muted)">Timing</div>' + timingContent;
    }

    // Populate Composition tab with actual data
    if (els.tabComp) {
      const compositionHtml = generateCompositionTable(prod);
      els.tabComp.innerHTML = compositionHtml;
    }

    // Update Reviews tab with beta message
    if (els.tabRev) {
      els.tabRev.innerHTML = `
        <div style="text-align:center;padding:40px 20px;">
          <p style="font-size:16px;color:#6b7280;margin-bottom:16px;">🚧 Reviews feature is coming soon!</p>
          <p style="font-size:14px;color:#9ca3af;">We're working hard to bring you authentic customer reviews. Check back soon!</p>
        </div>
      `;
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

  // Re-open the current product when language changes to fetch translated fields
  bus.addEventListener('language:changed', () => {
    const prod = (window && window.location && window.location.hash || '').slice(1);
    const m = prod.match(/^\/p\/([^/]+)$/);
    if (m && m[1]) {
      // Trigger the same open-product flow
      bus.dispatchEvent(new CustomEvent('open-product', { detail: { id: m[1] } }));
    }
  });
}

export function openProduct(id){
  // Bridge for the router → PDP
  bus.dispatchEvent(new CustomEvent('open-product', { detail: { id } }));
}


