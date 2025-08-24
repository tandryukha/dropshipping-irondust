import { $, $$ } from '../core/dom.js';
import { searchProducts } from '../api/api.js';
import { openFor } from './flavor-popover.js';
import { navigate } from '../core/router.js';

// State management
const searchState = {
  activeFilters: new Map(),
  activePreset: null,
  sortOrder: 'relevance'
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

export function mountSearchPanel() {
  const searchInput = $('#search');
  const searchPanel = $('#searchPanel');
  const productsList = $('#productsList');
  const allChips = $$('.chip[role="switch"]');
  const sortDropdown = $('#sortDropdown');
  
  // Setup chip overflow
  setupChipOverflow();
  
  // Handle sort dropdown
  sortDropdown?.addEventListener('change', (e) => {
    searchState.sortOrder = e.target.value;
    runFilteredSearch();
  });

  // Focus show/hide
  searchInput?.addEventListener('focus', ()=>{
    searchPanel?.classList.add('visible');
    // Re-check overflow when panel becomes visible
    setTimeout(() => {
      const checkOverflow = window.checkChipOverflow;
      if (checkOverflow) checkOverflow();
    }, 10);
  });
  document.addEventListener('click', (e)=>{
    // Don't close if clicking inside modals
    if (e.target.closest('.modal') || e.target.closest('.modal-overlay')) return;
    
    const inside = e.target.closest('.search-wrap') || e.target.closest('#searchPanel');
    if(!inside) searchPanel?.classList.remove('visible');
  });
  
  // Handle preset cards (both in panel and modal)
  function setupPresetCards() {
    $$('.preset-card').forEach(card => {
      if (card.__hasPresetHandler) return;
      card.__hasPresetHandler = true;
      
      card.addEventListener('click', () => {
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
        // Apply filters from preset
        for (const [key, value] of Object.entries(filters)) {
          allChips.forEach(chip => {
            const chipFilter = JSON.parse(chip.getAttribute('data-filter') || '{}');
            if (JSON.stringify(chipFilter) === JSON.stringify({ [key]: value })) {
              chip.setAttribute('aria-pressed', 'true');
              const label = chip.textContent.trim().replace(/\s+/g, ' ');
              searchState.activeFilters.set(chip, label);
            }
          });
        }
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
  
  // Handle modal buttons
  const moreGoalsBtn = $('#moreGoalsBtn');
  const goalsModal = $('#goalsModal');
  const goalsOverlay = $('#goalsOverlay');
  const goalsClose = $('#goalsClose');
  
  moreGoalsBtn?.addEventListener('click', () => {
    goalsModal.style.display = '';
    goalsOverlay.style.display = '';
    setupPresetCards(); // Re-setup for any new cards
  });
  
  goalsClose?.addEventListener('click', (e) => {
    e.stopPropagation();
    goalsModal.style.display = 'none';
    goalsOverlay.style.display = 'none';
  });
  
  goalsOverlay?.addEventListener('click', (e) => {
    e.stopPropagation();
    goalsModal.style.display = 'none';
    goalsOverlay.style.display = 'none';
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
  }

  async function runFilteredSearch(){
    if(!productsList) return;
    const query = (searchInput?.value||'').trim();
    const filters = getActiveFilters();
    productsList.innerHTML = '<div class="muted" style="padding:8px">Searching…</div>';
    try{
      // Temporarily disable sort parameter until backend supports it
      const searchParams = { 
        size: 6, 
        filters
      };
      
      // TODO: Add sort when backend supports it
      // if (searchState.sortOrder !== 'relevance') {
      //   searchParams.sort = searchState.sortOrder;
      // }
      
      const data = await searchProducts(query, searchParams);
      let items = Array.isArray(data?.items) ? data.items : [];
      
      // Client-side sorting as a temporary solution
      if (searchState.sortOrder !== 'relevance' && items.length > 0) {
        items = [...items].sort((a, b) => {
          switch(searchState.sortOrder) {
            case 'price_asc':
              return (a.price_cents || 0) - (b.price_cents || 0);
            case 'price_desc':
              return (b.price_cents || 0) - (a.price_cents || 0);
            case 'popular':
              return (b.rating || 0) - (a.rating || 0);
            case 'new':
              // Assuming newer items have higher IDs
              return (b.id || '').localeCompare(a.id || '');
            default:
              return 0;
          }
        });
      }
      
      if(items.length === 0){ productsList.innerHTML = '<div class="muted" style="padding:8px">No results</div>'; return; }
      productsList.innerHTML = items.map(renderProductHTML).join('');
      attachAddHandlers(productsList);
      attachOpenHandlers(productsList);
    }catch(e){
      console.error('Search error:', e);
      productsList.innerHTML = '<div class="muted" style="padding:8px">Search failed. Check API.</div>';
    }
  }
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
    productsList.innerHTML = '<div class="muted" style="padding:8px">Searching…</div>';
    try{
      const searchParams = { 
        size: 6, 
        filters: getActiveFilters()
      };
      
      const data = await searchProducts(query, searchParams);
      let items = Array.isArray(data?.items) ? data.items : [];
      
      // Client-side sorting as a temporary solution
      if (searchState.sortOrder !== 'relevance' && items.length > 0) {
        items = [...items].sort((a, b) => {
          switch(searchState.sortOrder) {
            case 'price_asc':
              return (a.price_cents || 0) - (b.price_cents || 0);
            case 'price_desc':
              return (b.price_cents || 0) - (a.price_cents || 0);
            case 'popular':
              return (b.rating || 0) - (a.rating || 0);
            case 'new':
              return (b.id || '').localeCompare(a.id || '');
            default:
              return 0;
          }
        });
      }
      
      if(items.length === 0){ productsList.innerHTML = '<div class="muted" style="padding:8px">No results</div>'; return; }
      productsList.innerHTML = items.map(renderProductHTML).join('');
      attachAddHandlers(productsList);
      attachOpenHandlers(productsList);
    }catch(e){
      console.error('Search error:', e);
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
}


