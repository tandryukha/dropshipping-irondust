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

// Theme handling (sporty=default, minimal, dense)
const THEME_KEY = 'theme';
const THEMES = ['sporty','minimal','dense'];

function applyTheme(theme){
  const root = document.documentElement;
  if (theme && theme !== 'sporty') {
    root.setAttribute('data-theme', theme);
  } else {
    root.removeAttribute('data-theme');
  }
}

function setTheme(theme, { persist=true, updateUrl=true } = {}){
  const normalized = THEMES.includes(theme) ? theme : 'sporty';
  applyTheme(normalized);
  if (persist) try { localStorage.setItem(THEME_KEY, normalized); } catch(_){ }
  if (updateUrl) {
    try {
      const url = new URL(window.location.href);
      if (normalized === 'sporty') url.searchParams.delete('theme'); else url.searchParams.set('theme', normalized);
      history.replaceState(null, '', url.toString());
    } catch(_){ }
  }
  const btn = document.getElementById('themeBtn');
  if (btn) {
    btn.setAttribute('aria-label', `Theme: ${normalized} (click to switch)`);
    btn.title = `Theme: ${normalized}`;
  }
}

function initTheme(){
  let fromQuery = null;
  try {
    const u = new URL(window.location.href);
    fromQuery = u.searchParams.get('theme');
  } catch(_){ }
  const fromStorage = (()=>{ try { return localStorage.getItem(THEME_KEY); } catch(_){ return null; } })();
  const initial = fromQuery || fromStorage || 'sporty';
  setTheme(initial, { persist: !!fromQuery, updateUrl: true });
}

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

  // Theme switcher button cycles sporty -> minimal -> dense
  const themeBtn = document.getElementById('themeBtn');
  if (themeBtn) {
    themeBtn.addEventListener('click', ()=>{
      const current = (()=>{ try { return localStorage.getItem(THEME_KEY) || 'sporty'; } catch(_){ return 'sporty'; } })();
      const idx = THEMES.indexOf(current);
      const next = THEMES[(idx + 1) % THEMES.length];
      setTheme(next);
    });
  }
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
initTheme();
mountHeader();
mountLanguageSelector();
mountFlavorPopover();
mountSearchPanel();
mountPdp();
mountHome();
mountAdmin();
start(); // start router

// Wire header CTA to open search overlay; sticky CTA is configured per route
(() => {
  const openBtn = document.getElementById('openSearchCta');
  openBtn?.addEventListener('click', ()=>{ try{ if(window.__openSearchOverlay) window.__openSearchOverlay(); }catch(_){ } });
})();


