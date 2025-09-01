import { $, $$ } from './core/dom.js';
import { bus } from './core/bus.js';
import { store } from './core/store.js';
import { route, start } from './core/router.js';
import { mountFlavorPopover } from './ui/flavor-popover.js';
import { mountSearchPanel } from './ui/search-panel.js';
import { mountPdp, openProduct } from './ui/pdp.js';
import { mountHome, showHome, hideHome } from './ui/home.js';
import { mountLanguageSelector } from './ui/language-selector.js';
import './ui/translations.js'; // Auto-applies translations

// Wire header bits that remain static
function mountHeader() {
  // Command palette (kept minimal)
  const overlay = $('#overlay'), palette = $('#palette'), palInput = $('#palInput'), boltBtn = $('#boltBtn');
  function openPal(){ overlay.classList.add('visible'); palette.classList.add('visible'); palInput.focus(); }
  function closePal(){ overlay.classList.remove('visible'); palette.classList.remove('visible'); palInput.blur(); }
  boltBtn?.addEventListener('click', openPal);
  overlay?.addEventListener('click', closePal);
  document.addEventListener('keydown',(e)=>{
    if((e.metaKey||e.ctrlKey) && e.key.toLowerCase()==='k'){ e.preventDefault(); palette.classList.contains('visible')?closePal():openPal(); }
    if(e.key==='Escape' && palette.classList.contains('visible')) closePal();
  });

  // Reference overlay
  const refBtn = $('#refBtn'), refImage = $('#refImage');
  refBtn?.addEventListener('click', ()=>{
    const on = refImage.classList.toggle('visible');
    refBtn.setAttribute('aria-pressed', String(on));
  });

  // Sync cart pill from store
  const cartCount = $('#cartCount');
  bus.addEventListener('store:cartCount', ()=>{ cartCount.textContent = String(store.get('cartCount')); });
}

// Routes
route('/', ()=>{ 
  // Show home, hide PDP
  showHome();
  const pdp = document.querySelector('.pdp');
  pdp?.classList.add('hidden');
  // Ensure header extras visible on home
  const headerSort = document.querySelector('.header-sort');
  const goalChips = document.querySelector('#goalChips');
  if (headerSort) headerSort.style.display = '';
  // Hide goal chips on home to avoid duplication with Featured chips
  if (goalChips) goalChips.style.display = 'none';
});
route('/p/:id', (id)=>{ 
  // Ensure search panel is hidden when opening PDP via routing
  const sp = $('#searchPanel');
  sp?.classList.remove('visible');
  const si = $('#search');
  si?.blur();
  // Hide home, show PDP
  hideHome();
  const pdp = document.querySelector('.pdp');
  pdp?.classList.remove('hidden');
  // Hide header extras on PDP
  const headerSort = document.querySelector('.header-sort');
  const goalChips = document.querySelector('#goalChips');
  if (headerSort) headerSort.style.display = 'none';
  if (goalChips) goalChips.style.display = 'none';
  openProduct(id); 
});

// Boot
mountHeader();
mountLanguageSelector();
mountFlavorPopover();
mountSearchPanel();
mountPdp();
mountHome();
start(); // start router


