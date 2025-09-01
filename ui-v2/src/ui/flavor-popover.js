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
    store.set('cartCount', store.get('cartCount') + currentQty);
    $('#cartCount').textContent = String(store.get('cartCount'));
    close();
  });
}

export function openFor(button, { initialFlavor = null, initialQty = 1 } = {}) {
  const r = button.getBoundingClientRect();
  pop.style.left = (r.left + window.scrollX - 40) + 'px';
  pop.style.top  = (r.top + window.scrollY - 10 - pop.offsetHeight/2) + 'px';

  title.textContent = 'Add: ' + (button.dataset.name || 'Product');
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


