import { $, $$ } from './core/dom.js';
import { bus } from './core/bus.js';
import { store } from './core/store.js';
import { route, start } from './core/router.js';
import { mountFlavorPopover } from './ui/flavor-popover.js';
import { mountSearchPanel } from './ui/search-panel.js';
import { mountPdp, openProduct } from './ui/pdp.js';

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
route('/', ()=>{ /* home/search state — nothing to fetch immediately */ });
route('/p/:id', (id)=>{ openProduct(id); });

// Boot
mountHeader();
mountFlavorPopover();
mountSearchPanel();
mountPdp();
start(); // start router


