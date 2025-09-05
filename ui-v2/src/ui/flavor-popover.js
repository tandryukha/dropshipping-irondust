import { $, $$ } from '../core/dom.js';
import { store } from '../core/store.js';

let pop, flavorWrap, confirmAdd, flavorSubtitle, qtyMinus, qtyPlus, qtyVal, title, btnClose;
let currentFlavor = null, currentQty = 1;

export function mountFlavorPopover() {
  pop = $('#flavorPop');
  flavorWrap = $('#flavorChips');
  confirmAdd = $('#confirmAdd');
  flavorSubtitle = $('#flavorSubtitle');
  qtyMinus = $('#qtyMinus');
  qtyPlus = $('#qtyPlus');
  qtyVal = $('#qtyVal');
  title = $('#flavorTitle');
  btnClose = $('#popClose');

  btnClose?.addEventListener('click', close);
  document.addEventListener('keydown', (e)=>{ if(e.key==='Escape' && pop.classList.contains('visible')) close(); });

  qtyMinus?.addEventListener('click', ()=>{ setQty(Math.max(1, currentQty-1)); });
  qtyPlus?.addEventListener('click', ()=>{ setQty(currentQty+1); });

  confirmAdd?.addEventListener('click', ()=>{
    // Update cart counters and subtotal (assume price on trigger dataset if present)
    const priceCents = Number(confirmAdd.getAttribute('data-price-cents')||'0') || 0;
    const nextCount = store.get('cartCount') + currentQty;
    const nextSubtotal = (Number(store.get('cartSubtotalCents')||0) + (priceCents*currentQty))|0;
    store.set('cartCount', nextCount);
    store.set('cartSubtotalCents', nextSubtotal);
    updateCartDrawer();
    close();
  });
}

export function openFor(button, { initialFlavor = null, initialQty = 1 } = {}) {
  const r = button.getBoundingClientRect();
  pop.style.left = (r.left + window.scrollX - 40) + 'px';
  pop.style.top  = (r.top + window.scrollY - 10 - pop.offsetHeight/2) + 'px';

  title.textContent = 'Add: ' + (button.dataset.name || 'Product');
  // Pass price cents into confirm for subtotal math if provided by trigger
  if (button.dataset.priceCents) confirmAdd.setAttribute('data-price-cents', String(button.dataset.priceCents));
  const list = JSON.parse(button.dataset.flavors || '[]');
  flavorWrap.innerHTML = '';
  currentFlavor = null;
  confirmAdd.disabled = false;

  if(list.length) {
    confirmAdd.disabled = true;
    flavorSubtitle.textContent = 'Select flavor (required)';
    list.forEach(fl=>{
      const b = document.createElement('button');
      b.className = 'flavor';
      b.setAttribute('aria-pressed','false');
      b.textContent = fl;
      b.addEventListener('click', ()=>{
        Array.from(flavorWrap.children).forEach(x=>x.setAttribute('aria-pressed','false'));
        b.setAttribute('aria-pressed','true');
        currentFlavor = fl;
        confirmAdd.disabled = false;
      });
      flavorWrap.appendChild(b);
    });
    if(initialFlavor){
      Array.from(flavorWrap.children).forEach(x=>{ if(x.textContent === initialFlavor) x.click(); });
    }
  } else {
    flavorSubtitle.textContent = 'No flavor options';
  }

  setQty(Math.max(1, parseInt(initialQty,10) || 1));
  pop.classList.add('visible');
  confirmAdd.focus();
}

export function close(){ pop.classList.remove('visible'); }
function setQty(n){ currentQty = n; qtyVal.textContent = String(currentQty); }

function updateCartDrawer(){
  try{
    const sub = document.getElementById('cartSubtotal');
    const cents = Number(store.get('cartSubtotalCents')||0);
    if (sub) sub.textContent = '€'+(cents/100).toFixed(2);
    // Free shipping progress (threshold 50€)
    const threshold = 5000;
    const msg = document.getElementById('freeShipMsg');
    const bar = document.getElementById('freeShipProg');
    const remain = Math.max(0, threshold - cents);
    if (msg) msg.textContent = remain>0 ? (`Add €${(remain/100).toFixed(2)} for free shipping`) : 'Free shipping unlocked!';
    if (bar) bar.style.width = Math.min(100, Math.round((cents/threshold)*100)) + '%';
  }catch(_e){}
}


