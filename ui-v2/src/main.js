import { $, $$ } from './core/dom.js';
import { bus } from './core/bus.js';
import { store } from './core/store.js';
import { route, start, navigate } from './core/router.js';
import { mountFlavorPopover } from './ui/flavor-popover.js';
import { mountSearchPanel } from './ui/search-panel.js';
import { mountPdp, openProduct } from './ui/pdp.js';
import { mountHome, showHome, hideHome } from './ui/home.js';
import { mountLanguageSelector } from './ui/language-selector.js';
import './ui/translations.js'; // Auto-applies translations
import { mountAdmin } from './ui/admin.js';

// Wire header bits that remain static
function mountHeader() {
  // No command palette; only '/' shortcut handled in search-panel

  // Reference overlay
  const refBtn = $('#refBtn'), refImage = $('#refImage');
  refBtn?.addEventListener('click', ()=>{
    const on = refImage.classList.toggle('visible');
    refBtn.setAttribute('aria-pressed', String(on));
  });

  // Sync cart pill from store
  const cartCount = $('#cartCount');
  bus.addEventListener('store:cartCount', ()=>{ cartCount.textContent = String(store.get('cartCount')); });

  // Cart drawer open/close
  const cartBtn = document.getElementById('cartBtn');
  const drawer = document.getElementById('cartDrawer');
  const cartClose = document.getElementById('cartClose');
  const toggle = (on)=>{
    if (!drawer) return;
    drawer.classList.toggle('visible', !!on);
    drawer.setAttribute('aria-hidden', on? 'false' : 'true');
  };
  cartBtn?.addEventListener('click', ()=> toggle(true));
  cartBtn?.addEventListener('keydown', (e)=>{ if(e.key==='Enter'||e.key===' ') { e.preventDefault(); toggle(true);} });
  cartClose?.addEventListener('click', ()=> toggle(false));
}

// Routes
route('/', ()=>{ 
  // Show home, hide PDP
  showHome();
  const pdp = document.querySelector('.pdp');
  pdp?.classList.add('hidden');
  const headerSort = document.querySelector('.header-sort');
  const goalChips = document.querySelector('#goalChips');
  if (headerSort) headerSort.style.display = '';
  if (goalChips) goalChips.style.display = 'none';
  // Ensure overlay is closed on home unless explicitly opened
  if (window.__closeSearchOverlay) window.__closeSearchOverlay();
});
route('/search', ()=>{
  // Open overlay, keep home visible underneath
  showHome();
  const pdp = document.querySelector('.pdp');
  pdp?.classList.add('hidden');
  if (window.__openSearchOverlay) window.__openSearchOverlay();
});
route('/p/:id', (id)=>{ 
  // Ensure search panel is hidden when opening PDP via routing
  if (window.__closeSearchOverlay) window.__closeSearchOverlay();
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
mountAdmin();
start(); // start router


