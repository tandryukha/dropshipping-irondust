import { $, $$, sanitizeHtml } from '../core/dom.js';
import { bus } from '../core/bus.js';
import { store } from '../core/store.js';
import { getProduct } from '../api/api.js';
import { openFor } from './flavor-popover.js';
import { t } from '../core/language.js';
import { isCountBasedForm } from '../core/metrics.js';

const els = {};

// Helper function to extract dosage information from search_text
function extractDosageFromText(searchText) {
  if (!searchText) return null;
  
  // Multi-language dosage patterns
  const dosagePatterns = [
    // Prefer explicit "Usage:" blocks; capture only the first sentence
    /(Kasutamine|Применение|Usage)\s*:\s*([^\.\!\n]+)/i,
    // Explicit daily dose labels
    /Soovitatav\s+p[aä]evane\s+annus:\s*([^\.\!\n]+)/i,
    /P[aä]evane\s+tarbimine:\s*([^\.\!\n]+)/i,
    /Рекомендуемая\s+суточная\s+доза:\s*([^\.\!\n]+)/i,
    /Recommended\s+daily\s+dose:\s*([^\.\!\n]+)/i,
    // Common free-text formats (kept short to avoid swallowing long descriptions)
    /(\d+\s*m[õo]õtelusikas[^\.\!\n]{0,120})/i,
    /(\d+\s*капсул[^\.\!\n]{0,120})/i,
    /(\d+\s*мерн(?:ая|ых)?\s*ложк[^\.\!\n]{0,120})/i,
    /(\d+\s*(?:capsules?|tabs?|tablets?|scoops?)[^\.\!\n]{0,120})/i
    // Intentionally no generic "servings" fallback to avoid noisy captures.
  ];
  
  for (const pattern of dosagePatterns) {
    const match = searchText.match(pattern);
    if (match) {
      const raw = (match[2] ?? match[1]).trim();
      // Clamp overly long captures to sentence boundary to avoid unrelated text
      if (raw.length > 160) {
        const cut = raw.slice(0, 160);
        const boundary = Math.max(cut.lastIndexOf('.'), Math.max(cut.lastIndexOf(','), cut.lastIndexOf(' ')));
        return (boundary > 40 ? cut.slice(0, boundary) : cut).trim();
      }
      return raw;
    }
  }
  return null;
}

// Helper function to extract timing information from search_text
function extractTimingFromText(searchText) {
  if (!searchText) return null;
  
  // Look for patterns related to timing (multi-language)
  const timingPatterns = [
    /(\d+[-–]\d+\s*minutit\s*enne\s*treeningut)/i,
    /(enne\s*treeningut[^\.!\n]*)/i,
    /(p[aä]rast\s*treeningut[^\.!\n]*)/i,
    /(за\s*\d+[-–]?\d*\s*мин[^\.!\n]*\s*до\s*тренировки)/i,
    /(после\s*тренировк[^\.!\n]*)/i,
    /(post[-\s]?workout[^\.!\n]*)/i,
    /(pre[-\s]?workout[^\.!\n]*)/i,
    /(before\s*training[^\.!\n]*)/i,
    /(after\s*training[^\.!\n]*)/i
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
  if (!prod) return '<p class="muted" style="font-size:14px;">'+t('pdp.fallback.no_composition','Composition information not available. Please check product packaging for details.')+'</p>';
  
  // Extract composition from search_text if available
  const searchText = prod.search_text || '';
  
  // Look for composition section in the text
  const compositionMatch = (
    searchText.match(/Koostisosad[^:]*:([^\n\r]+)/i) ||
    searchText.match(/Ингредиенты[^:]*:([^\n\r]+)/i) ||
    searchText.match(/Ingredients[^:]*:([^\n\r]+)/i)
  );
  if (compositionMatch) {
    const ingredients = compositionMatch[1].trim();
    const ingredientsList = ingredients.split(/[,;]/).map(ing => ing.trim()).filter(ing => ing);
    
    let html = '<div style="margin-bottom:16px;">';
    html += '<h4 style="margin:0 0 8px;font-size:14px;">'+t('pdp.ingredients','Ingredients')+'</h4>';
    html += '<ul style="margin:0;padding-left:20px;">';
    ingredientsList.forEach(ing => {
      html += `<li style="margin:4px 0;">${ing}</li>`;
    });
    html += '</ul>';
    html += '</div>';
    
    // Add any nutritional info if available
    if (prod.serving_size_g || prod.net_weight_g) {
      html += '<div style="margin-top:16px;">';
      html += '<h4 style="margin:0 0 8px;font-size:14px;">'+t('pdp.product_info','Product Information')+'</h4>';
      html += '<table style="width:100%;border-collapse:collapse;">';
      html += '<tbody>';
      if (prod.net_weight_g) {
        html += `<tr><td style="padding:8px;border:1px solid #e5e7eb;">${t('pdp.net_weight','Net weight')}</td><td style="padding:8px;border:1px solid #e5e7eb;">${prod.net_weight_g} g</td></tr>`;
      }
      if (prod.serving_size_g) {
        html += `<tr><td style="padding:8px;border:1px solid #e5e7eb;">${t('pdp.serving_size','Serving size')}</td><td style="padding:8px;border:1px solid #e5e7eb;">${prod.serving_size_g} g</td></tr>`;
      }
      if (prod.servings) {
        html += `<tr><td style="padding:8px;border:1px solid #e5e7eb;">${t('pdp.servings','Servings')}</td><td style="padding:8px;border:1px solid #e5e7eb;">${prod.servings}</td></tr>`;
      }
      html += '</tbody></table>';
      html += '</div>';
    }
    
    return html;
  }
  
  // Fallback to basic product info
  return '<p class="muted" style="font-size:14px;">'+t('pdp.fallback.no_composition','Composition information not available. Please check product packaging for details.')+'</p>';
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
    pdpBenefitSnippet: $('#pdpBenefitSnippet'),
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
      if (els.pdpBenefitSnippet) {
        const bs = (typeof prod?.benefit_snippet === 'string' ? prod.benefit_snippet : '').trim();
        els.pdpBenefitSnippet.textContent = bs ? (bs.length > 160 ? (bs.slice(0,157).trim() + '…') : bs) : '';
      }
      if (Array.isArray(prod?.images) && prod.images.length) {
        els.pdpImage.src = prod.images[0];
        els.pdpImage.alt = prod?.name || 'Product image';
      }
      // Description: prefer language-specific description (may contain HTML), fallback to search_text
      const rawDesc = typeof prod?.description === 'string' && prod.description.trim() ? prod.description : (typeof prod?.search_text === 'string' ? prod.search_text : '');
      if (els.pdpDescText) {
        if (/^\s*</.test(rawDesc)) {
          els.pdpDescText.innerHTML = sanitizeHtml(rawDesc);
        } else {
          els.pdpDescText.textContent = rawDesc;
        }
      }

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
        const isCountBased = isCountBasedForm(form);
        if (form) facts.push(`${t('pdp.form','Form')}: ${form}`);
        if (!isCountBased) {
          if (typeof prod?.net_weight_g === 'number') facts.push(`${t('pdp.net','Net')}: ${prod.net_weight_g} g`);
          if (typeof prod?.serving_size_g === 'number') facts.push(`${t('pdp.serving_size','Serving size')}: ${prod.serving_size_g} g`);
          if (typeof prod?.servings === 'number') {
            facts.push(`${t('pdp.servings','Servings')}: ${prod.servings}`);
          } else if (typeof prod?.servings_min === 'number' && typeof prod?.servings_max === 'number') {
            facts.push(`${t('pdp.servings','Servings')}: ${prod.servings_min}–${prod.servings_max}`);
          }
          if (typeof prod?.price_per_serving === 'number') facts.push(`${t('pdp.per_serving','Per serving')}: ${formatEuro(prod.price_per_serving, prod?.currency||'EUR')}`);
          if (typeof prod?.price_per_100g === 'number') facts.push(`${t('pdp.per_100g','Per 100g')}: ${formatEuro(prod.price_per_100g, prod?.currency||'EUR')}`);
        } else {
          if (typeof prod?.unit_count === 'number') facts.push(`${t('pdp.units','Units')}: ${prod.unit_count}`);
          if (typeof prod?.units_per_serving === 'number') facts.push(`${t('pdp.dose','Dose')}: ${prod.units_per_serving} ${form === 'tabs' ? 'tabs' : 'caps'}`);
          if (typeof prod?.unit_mass_g === 'number') facts.push(`Per ${form === 'tabs' ? 'tab' : 'cap'}: ${Math.round(prod.unit_mass_g*1000)} mg`);
          if (typeof prod?.price_per_unit === 'number') facts.push(`${t('pdp.per_unit','Per unit')}: ${formatEuro(prod.price_per_unit, prod?.currency||'EUR')}`);
                  // If units_per_serving present, compute servings; else prefer not to show derived servings to avoid confusion
        if (typeof prod?.units_per_serving === 'number' && prod.units_per_serving > 0 && typeof prod?.unit_count === 'number') {
          const srv = Math.floor(prod.unit_count / prod.units_per_serving);
          if (srv > 0) facts.push(`${t('pdp.servings','Servings')}: ${srv}`);
        }
      }
      // Remove goal tags from PDP facts per requirement
      // if (Array.isArray(prod?.goal_tags)) prod.goal_tags.forEach(t=>{ if(typeof t==='string'&&t) facts.push(t); });
      if (Array.isArray(prod?.diet_tags)) prod.diet_tags.forEach(t=>{ if(typeof t==='string'&&t) facts.push(t); });
      els.pdpFacts.innerHTML = facts.map(x=>`<span class="fact">${x}</span>`).join('');
    }

    // Update Dosage and Timing boxes dynamically
    if (els.dosageBox) {
      const dosageContent = (typeof prod?.dosage_text === 'string' && prod.dosage_text.trim())
        ? prod.dosage_text.trim()
        : (extractDosageFromText(prod?.search_text) || t('pdp.fallback.dosage','2 capsules per day with food'));
      els.dosageBox.innerHTML = '<div style="font-size:12px;color:var(--fp-muted)">'+t('pdp.dosage','Dosage')+'</div>' + dosageContent;
    }
    if (els.timingBox) {
      const timingContent = (typeof prod?.timing_text === 'string' && prod.timing_text.trim())
        ? prod.timing_text.trim()
        : (extractTimingFromText(prod?.search_text) || t('pdp.fallback.timing','With meals or within 30 min post-workout'));
      els.timingBox.innerHTML = '<div style="font-size:12px;color:var(--fp-muted)">'+t('pdp.timing','Timing')+'</div>' + timingContent;
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


