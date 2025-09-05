import { $, $$, sanitizeHtml } from '../core/dom.js';
import { bus } from '../core/bus.js';
import { store } from '../core/store.js';
import { getProduct, searchProducts, getAlternatives, getComplements } from '../api/api.js';
import { navigate } from '../core/router.js';
import { openFor } from './flavor-popover.js';
import { t } from '../core/language.js';
import { isCountBasedForm } from '../core/metrics.js';

const els = {};
let __lastAltCandidates = []; // cache for toggle re-rendering
let __altReqEpoch = 0; // prevent race conditions

// Bind the "Cheaper than this" toggle
function bindCheaperToggle() {
  const toggle = document.getElementById('altCheaperToggle');
  if (toggle && !toggle._boundCheaper) {
    toggle._boundCheaper = true;
    toggle.addEventListener('click', (e)=>{
      e.preventDefault();
      e.stopPropagation();
      const pressed = toggle.getAttribute('aria-pressed') === 'true';
      const next = !pressed;
      toggle.setAttribute('aria-pressed', String(next));
      store.set('altCheaper', next);
      // Trigger re-render immediately with cached data
      if (Array.isArray(__lastAltCandidates) && __lastAltCandidates.length) {
        renderAlternativesFromCandidates(__lastAltCandidates);
      }
    });
  }
}

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

// Compute per-serving savings badge text vs the currently viewed product.
// Returns a short string like "15% less/serv" or an empty string if not qualifying.
const PPS_SAVINGS_MIN_PERCENT = 0.15; // 15%
const PPS_SAVINGS_MIN_ABS = 0.05;     // €0.05 per serving
const PPS_SAVINGS_MAX_PERCENT = 0.5;  // cap shown percent at 50%
function computePerServingSavingsBadge(item, current){
  try{
    const a = derivePricePerServing(item);
    const b = derivePricePerServing(current);
    if (a == null || b == null) return '';
    if (!(a > 0 && b > 0)) return '';
    if (a >= b) return '';
    const abs = b - a;
    const pct = Math.max(0, Math.min(PPS_SAVINGS_MAX_PERCENT, abs / b));
    if (abs < PPS_SAVINGS_MIN_ABS) return '';
    if (pct < PPS_SAVINGS_MIN_PERCENT) return '';
    const pctInt = Math.round(pct * 100);
    return `${pctInt}% less/serv`;
  }catch(_e){ return ''; }
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
    pdpSubToggle: document.querySelector('#pdpSubToggle'),
    pdpWhy: document.querySelector('#pdpWhy'),
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
    // Alternatives containers
    pdpAltGrid: $('#pdpAltGrid'),
    pdpMoreGrid: $('#pdpMoreGrid'),
    altCheaperToggle: $('#altCheaperToggle'),
    altDietBadge: $('#altDietBadge'),
  });

  // Initialize "Cheaper than this" toggle state from store
  try {
    const initCheaper = !!store.get('altCheaper');
    if (els.altCheaperToggle) {
      els.altCheaperToggle.setAttribute('aria-pressed', String(initCheaper));
    }
  } catch(_e) {}

  // React to altCheaper changes anywhere
  bus.addEventListener('store:altCheaper', ()=>{
    try{
      const on = !!store.get('altCheaper');
      const toggle = document.getElementById('altCheaperToggle');
      if (toggle) {
        toggle.setAttribute('aria-pressed', String(on));
      }
      // Only re-render from cached candidates, don't trigger new API calls
      if (Array.isArray(__lastAltCandidates) && __lastAltCandidates.length) {
        renderAlternativesFromCandidates(__lastAltCandidates);
      }
      // Don't fetch new alternatives just because toggle changed - use cached data only
    }catch(_e){}
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

  // Bind Alternatives: "Cheaper than this" toggle initially
  bindCheaperToggle();

  // Keep Vegan badge in Alternatives header in sync with active filter/state
  try{
    const updateAltDietBadge = ()=>{
      try{
        const badge = els.altDietBadge;
        if (!badge) return;
        // Show if current product is vegan OR vegan filter chip is active
        const current = store.get('currentProduct');
        const isProductVegan = Array.isArray(current?.diet_tags) && current.diet_tags.includes('vegan');
        const veganChip = document.querySelector('.chip[data-filter*="\"diet_tags\":[\"vegan\"]"]');
        const isFilterOn = !!(veganChip && veganChip.getAttribute('aria-pressed') === 'true');
        badge.style.display = (isProductVegan || isFilterOn) ? '' : 'none';
      }catch(_e){}
    };
    // Initial sync shortly after mount
    setTimeout(updateAltDietBadge, 0);
    // Update when cheaper toggle changes (layout refresh) and when product loads
    bus.addEventListener('store:altCheaper', updateAltDietBadge);
    bus.addEventListener('store:currentProduct', updateAltDietBadge);
    // Observe changes to the vegan chip's aria-pressed attribute
    const veganChip = document.querySelector('.chip[data-filter*="\"diet_tags\":[\"vegan\"]"]');
    if (veganChip && !veganChip.__pdpObserved){
      veganChip.__pdpObserved = true;
      const mo = new MutationObserver(updateAltDietBadge);
      mo.observe(veganChip, { attributes:true, attributeFilter:['aria-pressed'] });
    }
    // Also re-evaluate after alternatives render
    setTimeout(updateAltDietBadge, 10);
  }catch(_e){}

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

  // Subscribe & Save toggle behavior (pure UI; business logic can hook later)
  try{
    const sub = els.pdpSubToggle;
    if (sub && !sub.__bound){
      sub.__bound = true;
      sub.addEventListener('click', ()=>{
        const on = sub.getAttribute('aria-pressed') === 'true';
        const next = !on;
        sub.setAttribute('aria-pressed', String(next));
        // Optional: reflect in button label
        sub.textContent = next ? 'Subscribe & Save 10%' : 'One‑time purchase';
        // Persist lightweight UI preference
        try{ localStorage.setItem('ui_subscribe_on', JSON.stringify(next)); }catch(_e){}
      });
      // Restore state
      try{ const saved = JSON.parse(localStorage.getItem('ui_subscribe_on')||'true'); sub.setAttribute('aria-pressed', String(!!saved)); if(!saved) sub.textContent='One‑time purchase'; }catch(_e){}
    }
  }catch(_e){}
  
  // Store reference to current product for tabs
  store.set('currentProduct', null);

  // Listen to route changes (open product)
  bus.addEventListener('open-product', async (e)=>{
    const id = e.detail?.id;
    if(!id) return;
    try{
      const prod = await getProduct(id);
      store.set('currentProduct', prod); // Store for tab updates
      els.pdpProductName.textContent = (prod?.display_title || prod?.name) || 'Product';
      els.pdpPrice.textContent = formatPrice(prod?.price_cents, prod?.currency||'EUR');
      els.pdpStars.textContent = renderStars(typeof prod?.rating === 'number' ? prod.rating : undefined);
      els.pdpReviewCount.textContent = String(prod?.review_count ?? '0');
      if (els.pdpBenefitSnippet) {
        const bs = (typeof prod?.benefit_snippet === 'string' ? prod.benefit_snippet : '').trim();
        els.pdpBenefitSnippet.textContent = bs ? (bs.length > 160 ? (bs.slice(0,157).trim() + '…') : bs) : '';
      }
      if (Array.isArray(prod?.images) && prod.images.length) {
        els.pdpImage.src = prod.images[0];
        els.pdpImage.alt = (prod?.display_title || prod?.name) || 'Product image';
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

      // Why these ingredients: show 3 bullets when benefit_snippet or search_text present
      try{
        if (els.pdpWhy){
          const lines = [];
          const snip = (typeof prod?.benefit_snippet==='string'?prod.benefit_snippet:'').trim();
          if (snip) lines.push('Formulated for: '+snip);
          const form = typeof prod?.form==='string'?prod.form:'';
          if (form) lines.push('Form factor optimized: '+form);
          const country = typeof prod?.origin_country==='string'?prod.origin_country:'';
          if (country) lines.push('Traceable origin: '+country);
          els.pdpWhy.innerHTML = lines.length?(`<strong>Why these ingredients?</strong><ul style="margin:6px 0 0 18px">${lines.slice(0,3).map(l=>`<li>${l}</li>`).join('')}</ul>`):'';
        }
      }catch(_e){}
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
      els.pdpAdd?.setAttribute('data-name', (prod?.display_title || prod?.name) || 'Product');
      if (typeof prod?.price_cents === 'number') els.pdpAdd?.setAttribute('data-price-cents', String(prod.price_cents));

      // Load alternatives and complements separately
      const altIds = await fetchAndRenderAlternatives(prod);
      await fetchAndRenderComplements(prod, new Set(Array.isArray(altIds)?altIds:[]));
      
      // Ensure toggle is bound after alternatives are loaded
      bindCheaperToggle();

      // After content is in the DOM, update scroll affordance states
      requestAnimationFrame(()=>{
        [els.pdpAltGrid, els.pdpMoreGrid].forEach(track=>{
          if (!track) return;
          const atStart = track.scrollLeft <= 1;
          const canScroll = track.scrollWidth - track.clientWidth - track.scrollLeft > 1;
          track.classList.toggle('can-scroll-left', !atStart);
          track.classList.toggle('can-scroll-right', canScroll);
          track.addEventListener('scroll', ()=>{
            const atStart2 = track.scrollLeft <= 1;
            const canScroll2 = track.scrollWidth - track.clientWidth - track.scrollLeft > 1;
            track.classList.toggle('can-scroll-left', !atStart2);
            track.classList.toggle('can-scroll-right', canScroll2);
          }, { passive:true });
        });
      });
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

// --- Alternatives rendering ---

// Sequence guard for in-flight alternatives requests to avoid stale renders overwriting newer ones
// (Variables declared at top of file)

// Safely compute price per serving when not explicitly provided
function derivePricePerServing(prod){
  try{
    if (typeof prod?.price_per_serving === 'number' && prod.price_per_serving > 0) return prod.price_per_serving;
    // From total price and servings
    if (typeof prod?.price_cents === 'number' && typeof prod?.servings === 'number' && prod.servings > 0) {
      return (prod.price_cents / 100) / prod.servings;
    }
    // From count-based dosage
    if (typeof prod?.price_cents === 'number' && typeof prod?.unit_count === 'number' && typeof prod?.units_per_serving === 'number' && prod.units_per_serving > 0) {
      const srv = Math.floor(prod.unit_count / prod.units_per_serving);
      if (srv > 0) return (prod.price_cents / 100) / srv;
    }
    // From price per 100g and serving size
    if (typeof prod?.price_per_100g === 'number' && typeof prod?.serving_size_g === 'number' && prod.serving_size_g > 0) {
      return (prod.price_per_100g * prod.serving_size_g) / 100;
    }
    // From net weight and serving size + price
    if (typeof prod?.price_cents === 'number' && typeof prod?.net_weight_g === 'number' && typeof prod?.serving_size_g === 'number' && prod.net_weight_g > 0 && prod.serving_size_g > 0) {
      const pricePerGram = (prod.price_cents / 100) / prod.net_weight_g;
      return pricePerGram * prod.serving_size_g;
    }
  }catch(_e){}
  return null;
}

// Optionally filter to items cheaper per serving than the current product
function maybeFilterCheaper(items, current){
  try{
    const enabled = !!store.get('altCheaper');
    if (!enabled) return Array.isArray(items) ? items : [];
    // Try comparable metrics in order: per-serving, per-100g, per-unit
    const list = Array.isArray(items) ? items : [];
    return list.filter(it => {
      const a = derivePricePerServing(it), b = derivePricePerServing(current);
      if (typeof a === 'number' && a > 0 && typeof b === 'number' && b > 0) return a < b;
      const a100 = derivePricePer100g(it), b100 = derivePricePer100g(current);
      if (typeof a100 === 'number' && a100 > 0 && typeof b100 === 'number' && b100 > 0) return a100 < b100;
      const au = derivePricePerUnit(it), bu = derivePricePerUnit(current);
      if (typeof au === 'number' && au > 0 && typeof bu === 'number' && bu > 0) return au < bu;
      return false;
    });
  }catch(_e){ return Array.isArray(items) ? items : []; }
}

function derivePricePer100g(prod){
  try{
    if (typeof prod?.price_per_100g === 'number' && prod.price_per_100g > 0) return prod.price_per_100g;
    if (typeof prod?.price_cents === 'number' && typeof prod?.net_weight_g === 'number' && prod.net_weight_g > 0) {
      return (prod.price_cents / 100) / (prod.net_weight_g / 100);
    }
  }catch(_e){}
  return null;
}

function derivePricePerUnit(prod){
  try{
    if (typeof prod?.price_per_unit === 'number' && prod.price_per_unit > 0) return prod.price_per_unit;
    if (typeof prod?.price_cents === 'number' && typeof prod?.unit_count === 'number' && prod.unit_count > 0) {
      return (prod.price_cents / 100) / prod.unit_count;
    }
  }catch(_e){}
  return null;
}

function renderAlternativesFromCandidates(candidates){
  try{
    const myEpoch = __altReqEpoch; // only render if still latest context
    const current = store.get('currentProduct');
    let items = Array.isArray(candidates) ? candidates : [];
    items = maybeFilterCheaper(items, current);
    if (myEpoch !== __altReqEpoch) return;
    const alt = items.slice(0, 8);
    if (els.pdpAltGrid) {
      els.pdpAltGrid.innerHTML = alt.map((it, idx)=>buildAltCardHTML(it, idx)).join('');
      attachAltHandlers(els.pdpAltGrid);
    }
  }catch(_e){}
}

function altDetailsLine(item){
  const bits = [];
  const form = typeof item?.form === 'string' ? item.form : '';
  if (form) bits.push(form);
  if (typeof item?.serving_size_g === 'number') bits.push(`${item.serving_size_g} g/serv`);
  if (typeof item?.unit_count === 'number') bits.push(`${item.unit_count} pcs`);
  if (typeof item?.rating === 'number' && typeof item?.review_count === 'number' && item.review_count >= 3) {
    bits.push(`★ ${item.rating.toFixed(1)} (${item.review_count})`);
  }
  return bits.join(' • ');
}

function buildAltCardHTML(item, index=0){
  const img = (item?.images?.[0]) || 'https://picsum.photos/seed/alt/240/140';
  const name = (item?.display_title || item?.name) || '';
  const priceText = typeof item?.price_cents === 'number' ? formatPrice(item.price_cents, item?.currency||'EUR') : '';
  const details = altDetailsLine(item);
  const eager = index < 3; // load a few eagerly to avoid lazy-load stalls in sidecar
  const loading = eager ? 'eager' : 'lazy';
  const fetchpriority = eager ? 'high' : 'auto';

  // Optional reason chip to explain recommendation
  const current = store.get('currentProduct');
  const reason = (()=>{
    try{
      if (typeof item?.reason === 'string' && item.reason) return item.reason;
      const reasons = [];
      if (current && item?.brand_slug && current?.brand_slug && item.brand_slug === current.brand_slug) reasons.push('Same brand');
      if (current && item?.form && current?.form && item.form === current.form) reasons.push('Same form');
      if (current && typeof item?.rating === 'number' && typeof current?.rating === 'number' && item.rating >= current.rating + 0.3) reasons.push('Higher rated');
      if (current && typeof item?.price_per_serving === 'number' && typeof current?.price_per_serving === 'number' && item.price_per_serving < current.price_per_serving) reasons.push('Better €/serv');
      return reasons[0] || '';
    }catch(_){ return ''; }
  })();

  // Savings and per-serving
  const pricePerServing = typeof item?.price_per_serving === 'number' ? formatEuro(item.price_per_serving, item?.currency||'EUR') : '';
  const savings = (()=>{
    try{
      if (typeof item?.compare_at_cents === 'number' && typeof item?.price_cents === 'number' && item.compare_at_cents > item.price_cents) {
        const diff = (item.compare_at_cents - item.price_cents)/100;
        if (diff >= 0.5) return `Save €${diff.toFixed(0)}`;
      }
      return '';
    }catch(_){ return ''; }
  })();
  const ppsBadge = computePerServingSavingsBadge(item, current);

  // Optional benefit snippet to promote product value (clamped in CSS)
  const benefitSnippet = (typeof item?.benefit_snippet === 'string' ? item.benefit_snippet : '').trim();
  const benefitHtml = benefitSnippet ? `<div class="alt-snippet">${benefitSnippet.length > 140 ? (benefitSnippet.slice(0,137).trim()+"…") : benefitSnippet}</div>` : '';

  return `
    <div class="sku alt-card" data-id="${item?.id||''}" role="button" tabindex="0" aria-label="Open ${name}">
      <div class="alt-image skeleton"><img src="${img}" alt="${name}" loading="${loading}" decoding="async" fetchpriority="${fetchpriority}" width="240" height="180"></div>
      ${reason ? `<div class="alt-reason">${reason}</div>` : ''}
      <div class="alt-title">${name}</div>
      ${benefitHtml}
      <div class="alt-details muted">${details}</div>
      <div class="alt-price-row">
        <div class="alt-price">${priceText}</div>
        ${savings ? `<span class="alt-save">${savings}</span>` : ''}
        ${ppsBadge ? `<span class="alt-pps" title="vs this product’s price per serving">${ppsBadge}</span>` : ''}
      </div>
      ${pricePerServing ? `<div class="alt-subprice">${pricePerServing} / serv</div>` : ''}
    </div>`;
}

function buildMoreCardHTML(item, index=0){
  const img = (item?.images?.[0]) || 'https://picsum.photos/seed/more/120/120';
  const name = (item?.display_title || item?.name) || '';
  const priceText = typeof item?.price_cents === 'number' ? formatPrice(item.price_cents, item?.currency||'EUR') : '';
  const benefitSnippet = (typeof item?.benefit_snippet === 'string' ? item.benefit_snippet : '').trim();
  const benefitHtml = benefitSnippet ? `<div class="alt-snippet">${benefitSnippet.length > 90 ? (benefitSnippet.slice(0,87).trim()+"…") : benefitSnippet}</div>` : '';
  const pricePerServing = typeof item?.price_per_serving === 'number' ? formatEuro(item.price_per_serving, item?.currency||'EUR') : '';
  const eager = index < 2;
  const loading = eager ? 'eager' : 'lazy';
  return `
    <div class="sku alt-card" data-id="${item?.id||''}" role="button" tabindex="0" aria-label="Open ${name}">
      <div class="alt-image skeleton"><img src="${img}" alt="${name}" width="84" height="84" loading="${loading}" decoding="async"></div>
      <div class="alt-title">${name}</div>
      ${benefitHtml}
      <div class="alt-price">${priceText}</div>
      ${pricePerServing ? `<div class="alt-subprice">${pricePerServing} / serv</div>` : ''}
    </div>`;
}

function attachAltHandlers(container){
  if (!container) return;
  container.querySelectorAll('.sku').forEach(card=>{
    if (card.__boundAlt) return; card.__boundAlt = true;
    card.addEventListener('click', (e)=>{
      const id = card.getAttribute('data-id') || e.target.getAttribute('data-id');
      if (!id) return;
      navigate('/p/'+id);
      // Ensure PDP opens via bus
      bus.dispatchEvent(new CustomEvent('open-product', { detail: { id } }));
    });
    // Keyboard access
    card.addEventListener('keydown', (e)=>{
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        const id = card.getAttribute('data-id');
        if (!id) return;
        navigate('/p/'+id);
        bus.dispatchEvent(new CustomEvent('open-product', { detail: { id } }));
      }
    });
  });

  // Image skeleton + error fallback handling
  container.querySelectorAll('.alt-image img').forEach(img => {
    if (img.__boundLoad) return; img.__boundLoad = true;
    const box = img.closest('.alt-image');
    const removeSkeleton = () => { if (box) box.classList.remove('skeleton'); };
    const setFallback = () => {
      const w = Number(img.getAttribute('width')) || 240;
      const h = Number(img.getAttribute('height')) || 180;
      // Avoid infinite loop: only replace once
      if (!img.__fallbackSet) {
        img.__fallbackSet = true;
        img.src = `https://picsum.photos/seed/fallback/${w}/${h}`;
      }
    };
    const validate = () => {
      // Some hosts return anti-hotlink tiny placeholders; treat them as failure
      if (img.naturalWidth <= 40 || img.naturalHeight <= 40) {
        setFallback();
      }
      removeSkeleton();
    };
    if (img.complete && img.naturalWidth > 0) {
      validate();
    } else {
      img.addEventListener('load', validate, { once: true });
      img.addEventListener('error', () => { setFallback(); removeSkeleton(); }, { once: true });
    }
  });

  // Hint the browser to prefetch the next few images to reduce pop-in
  const imgs = Array.from(container.querySelectorAll('.alt-image img'));
  const toPrefetch = imgs.slice(1, 3); // next two after the first
  toPrefetch.forEach(srcImg => {
    const url = srcImg.getAttribute('src');
    if (!url) return;
    const i = new Image();
    i.decoding = 'async';
    i.loading = 'eager';
    i.src = url;
    // Optional: fire decode without blocking
    if (i.decode) { i.decode().catch(() => {}); }
  });
}

async function fetchAndRenderAlternatives(prod){
  try{
    // Bump epoch to invalidate any prior in-flight calls
    const myEpoch = ++__altReqEpoch;
    const latestCurrent = store.get('currentProduct') || prod;
    // Prefer backend recommendations if available
    try {
      const rec = await getAlternatives(String(prod?.id||''), { limit: 8 });
      let items = Array.isArray(rec?.items) ? rec.items : [];
      __lastAltCandidates = Array.isArray(items) ? items.slice() : [];
      // Optionally filter by cheaper than current
      items = maybeFilterCheaper(items, latestCurrent);
      // Abort render if a newer request started meanwhile
      if (myEpoch !== __altReqEpoch) return;
      if (items.length) {
        const alt = items.slice(0, 8);
        if (els.pdpAltGrid) { els.pdpAltGrid.innerHTML = alt.map(buildAltCardHTML).join(''); attachAltHandlers(els.pdpAltGrid); }
        return alt.map(x=>x.id);
      }
    } catch(_e) {
      // Fallback to client-side strategy below
    }

    // 1) Try semantic (hybrid) alternatives using product context
    const qParts = [];
    if (typeof prod?.name === 'string' && prod.name) qParts.push(prod.name);
    if (typeof prod?.form === 'string' && prod.form) qParts.push(prod.form);
    if (Array.isArray(prod?.categories_names) && prod.categories_names.length) qParts.push(prod.categories_names[0]);
    const q = qParts.join(' ');

    let resp = await searchProducts(q, { page: 1, size: 12, filters: { in_stock: true } });
    let items = Array.isArray(resp?.items) ? resp.items : [];
    // Exclude current product and same-variation siblings client-side
    items = items.filter(it => {
      if (!it) return false;
      if (prod?.id && it.id === prod.id) return false;
      if (prod?.parent_id && it.parent_id === prod.parent_id) return false;
      return true;
    });

    // 2) Fallback: category + form browse (rating sort)
    if (items.length < 4) {
      const catSlug = Array.isArray(prod?.categories_slugs) && prod.categories_slugs.length ? prod.categories_slugs[0] : null;
      const baseFilters = { in_stock: true };
      if (catSlug) baseFilters.categories_slugs = [catSlug];
      if (typeof prod?.form === 'string' && prod.form) baseFilters.form = prod.form;
      const r2 = await searchProducts('', { page: 1, size: 12, filters: baseFilters, sort: ['rating:desc','review_count:desc'] });
      let b = Array.isArray(r2?.items) ? r2.items : [];
      b = b.filter(it => {
        if (!it) return false;
        if (prod?.id && it.id === prod.id) return false;
        if (prod?.parent_id && it.parent_id === prod.parent_id) return false;
        return true;
      });
      // Merge unique by id, keeping existing order preference
      const seen = new Set(items.map(x=>x.id));
      for (const it of b) { if (!seen.has(it.id)) { items.push(it); seen.add(it.id); } }
    }

    // 3) Second fallback: brand-based
    if (items.length < 4 && typeof prod?.brand_slug === 'string' && prod.brand_slug) {
      const f3 = { in_stock: true, brand_slug: prod.brand_slug };
      const r3 = await searchProducts('', { page: 1, size: 12, filters: f3, sort: ['rating:desc','review_count:desc'] });
      let b = Array.isArray(r3?.items) ? r3.items : [];
      b = b.filter(it => {
        if (!it) return false;
        if (prod?.id && it.id === prod.id) return false;
        if (prod?.parent_id && it.parent_id === prod.parent_id) return false;
        return true;
      });
      const seen = new Set(items.map(x=>x.id));
      for (const it of b) { if (!seen.has(it.id)) { items.push(it); seen.add(it.id); } }
    }

    // 4) Final fallback: popular in-stock
    if (items.length < 4) {
      const r4 = await searchProducts('', { page: 1, size: 12, filters: { in_stock: true }, sort: ['rating:desc','review_count:desc'] });
      let b = Array.isArray(r4?.items) ? r4.items : [];
      b = b.filter(it => {
        if (!it) return false;
        if (prod?.id && it.id === prod.id) return false;
        if (prod?.parent_id && it.parent_id === prod.parent_id) return false;
        return true;
      });
      const seen = new Set(items.map(x=>x.id));
      for (const it of b) { if (!seen.has(it.id)) { items.push(it); seen.add(it.id); } }
    }

    // Apply optional cheaper-than-current filter as late as possible to keep variety
    __lastAltCandidates = Array.isArray(items) ? items.slice() : [];
    items = maybeFilterCheaper(items, latestCurrent);
    // Abort render if a newer request started meanwhile
    if (myEpoch !== __altReqEpoch) return;
    const alt = items.slice(0, 8);
    if (els.pdpAltGrid) {
      els.pdpAltGrid.innerHTML = alt.map((it, idx)=>buildAltCardHTML(it, idx)).join('');
      attachAltHandlers(els.pdpAltGrid);
    }
    return alt.map(x=>x.id);
  }catch(_e){
    try{
      if (els.pdpAltGrid) els.pdpAltGrid.innerHTML = '';
    }catch(_ignored){}
  }
}

async function fetchAndRenderComplements(prod, excludeIds=new Set()){
  try{
    // Prefer backend complements
    try {
      const rec = await getComplements(String(prod?.id||''), { limit: 8 });
      let items = Array.isArray(rec?.items) ? rec.items : [];
      if (excludeIds && excludeIds.size) items = items.filter(it => !excludeIds.has(it.id));
      if (items.length) {
        if (els.pdpMoreGrid) { els.pdpMoreGrid.innerHTML = items.map((it, idx)=>buildMoreCardHTML(it, idx)).join(''); attachAltHandlers(els.pdpMoreGrid); }
        return;
      }
    } catch(_e) { /* fall through */ }

    // Fallback: popular in-stock, excluding current and siblings
    const r = await searchProducts('', { page: 1, size: 12, filters: { in_stock: true }, sort: ['rating:desc','review_count:desc'] });
    let items = Array.isArray(r?.items) ? r.items : [];
    items = items.filter(it => it && it.id !== prod?.id && it.parent_id !== prod?.parent_id && !excludeIds.has(it.id));
    if (els.pdpMoreGrid) { els.pdpMoreGrid.innerHTML = items.slice(0,8).map((it, idx)=>buildMoreCardHTML(it, idx)).join(''); attachAltHandlers(els.pdpMoreGrid); }
  }catch(_e){
    try{ if (els.pdpMoreGrid) els.pdpMoreGrid.innerHTML = ''; }catch(_ignored){}
  }
}


