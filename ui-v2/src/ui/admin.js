import { $, $$ } from '../core/dom.js';
import { ingestProducts, getAdminRawSystem, getAdminRawWoo } from '../api/api.js';

function parseIds(input) {
  if (!input) return [];
  return input
    .split(/[\n,]+/)
    .map(s => s.trim())
    .filter(Boolean)
    .map(s => s.toLowerCase().startsWith('wc_') ? s.slice(3) : s.startsWith('wc') ? s.slice(2) : s)
    .map(s => s.replace(/[^0-9]/g, ''))
    .filter(Boolean)
    .map(s => Number(s))
    .filter(n => Number.isFinite(n));
}

function jsonPretty(obj) {
  try { return JSON.stringify(obj, null, 2); } catch { return String(obj); }
}

function copyToClipboard(text) {
  try { navigator.clipboard.writeText(text); } catch {}
}

export function mountAdmin() {
  const url = new URL(location.href);
  const isAdmin = url.searchParams.get('admin') === '1' || url.searchParams.get('admin') === 'true';
  if (!isAdmin) return;

  // Create compact admin panel
  const panel = document.createElement('div');
  panel.id = 'adminPanel';
  panel.style.position = 'fixed';
  panel.style.right = '12px';
  panel.style.bottom = '12px';
  panel.style.zIndex = '80';
  panel.style.background = '#ffffff';
  panel.style.border = '1px solid #e5e7eb';
  panel.style.borderRadius = '12px';
  panel.style.boxShadow = '0 10px 30px rgba(0,0,0,0.15)';
  panel.style.width = 'min(540px, 94vw)';
  panel.style.maxHeight = '70vh';
  panel.style.overflow = 'hidden';

  panel.innerHTML = `
    <div style="display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-bottom:1px solid #eef1f4">
      <strong>Admin Tools</strong>
      <button id="adminClose" style="border:0;background:transparent;font-size:18px;cursor:pointer;color:#6b7280">✕</button>
    </div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:10px;align-items:start">
      <div style="grid-column:1 / -1">
        <label for="adminIds" style="display:block;font-size:12px;color:#6b7280;margin-bottom:4px">IDs (comma/newline, like 31476 or wc_31476)</label>
        <textarea id="adminIds" rows="3" style="width:100%;border:1px solid #e5e7eb;border-radius:8px;padding:8px;font-family:ui-monospace, SFMono-Regular, Menlo, monospace"></textarea>
        <div style="display:flex;gap:8px;margin-top:8px;align-items:center">
          <input id="adminKey" placeholder="x-admin-key (optional)" style="flex:1;border:1px solid #e5e7eb;border-radius:8px;padding:8px"/>
          <label style="display:flex;gap:6px;align-items:center;font-size:12px;color:#374151"><input type="checkbox" id="adminClearAi"/> clear AI</label>
          <label style="display:flex;gap:6px;align-items:center;font-size:12px;color:#374151"><input type="checkbox" id="adminClearTr"/> clear TR</label>
          <button id="adminReingest" class="btn" style="border:1px solid #e0e6ef;background:#f8fafc;border-radius:8px;padding:8px 10px;font-weight:700;cursor:pointer">Reingest</button>
        </div>
      </div>
      <div>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
          <strong>System Raw</strong>
          <div>
            <button id="copySystem" class="btn" style="border:1px solid #e0e6ef;background:#f8fafc;border-radius:8px;padding:4px 8px;font-weight:700;cursor:pointer;font-size:12px">Copy</button>
            <button id="fetchSystem" class="btn" style="border:1px solid #e0e6ef;background:#f8fafc;border-radius:8px;padding:4px 8px;font-weight:700;cursor:pointer;font-size:12px">Fetch</button>
          </div>
        </div>
        <pre id="systemRaw" style="margin:0;overflow:auto;border:1px solid #eef1f4;border-radius:8px;padding:8px;background:#fcfdff;max-height:38vh"></pre>
      </div>
      <div>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
          <strong>Woo Raw</strong>
          <div>
            <button id="copyWoo" class="btn" style="border:1px solid #e0e6ef;background:#f8fafc;border-radius:8px;padding:4px 8px;font-weight:700;cursor:pointer;font-size:12px">Copy</button>
            <button id="fetchWoo" class="btn" style="border:1px solid #e0e6ef;background:#f8fafc;border-radius:8px;padding:4px 8px;font-weight:700;cursor:pointer;font-size:12px">Fetch</button>
          </div>
        </div>
        <pre id="wooRaw" style="margin:0;overflow:auto;border:1px solid #eef1f4;border-radius:8px;padding:8px;background:#fcfdff;max-height:38vh"></pre>
      </div>
    </div>
  `;

  document.body.appendChild(panel);

  const idsEl = panel.querySelector('#adminIds');
  const keyEl = panel.querySelector('#adminKey');
  const clearAiEl = panel.querySelector('#adminClearAi');
  const clearTrEl = panel.querySelector('#adminClearTr');
  const sysPre = panel.querySelector('#systemRaw');
  const wooPre = panel.querySelector('#wooRaw');

  keyEl.value = localStorage.getItem('adminKey') || '';
  keyEl.addEventListener('change', ()=>{
    localStorage.setItem('adminKey', keyEl.value.trim());
  });

  panel.querySelector('#adminClose').addEventListener('click', ()=>{
    panel.remove();
  });

  panel.querySelector('#adminReingest').addEventListener('click', async ()=>{
    const ids = parseIds(idsEl.value);
    if (ids.length === 0) { alert('Provide at least one numeric id'); return; }
    const btn = panel.querySelector('#adminReingest');
    btn.disabled = true; btn.textContent = 'Reingesting…';
    try {
      const report = await ingestProducts(ids, {
        clearAi: clearAiEl.checked,
        clearTranslation: clearTrEl.checked,
        adminKey: keyEl.value.trim() || undefined
      });
      alert(`Indexed ${report.indexed} items; warnings=${report.warnings_total}; conflicts=${report.conflicts_total}`);
    } catch (e) {
      alert('Failed: ' + (e?.message || e));
    } finally {
      btn.disabled = false; btn.textContent = 'Reingest';
    }
  });

  async function fetchOne(which) {
    const ids = parseIds(idsEl.value);
    if (ids.length === 0) { alert('Provide at least one id'); return; }
    const id = ids[0];
    const opts = { adminKey: keyEl.value.trim() || undefined };
    try {
      if (which === 'system') {
        const data = await getAdminRawSystem('wc_' + id, opts);
        sysPre.textContent = jsonPretty(data);
      } else {
        const data = await getAdminRawWoo(String(id), opts);
        wooPre.textContent = jsonPretty(data);
      }
    } catch (e) {
      const target = which === 'system' ? sysPre : wooPre;
      target.textContent = 'Error: ' + (e?.message || e);
    }
  }

  panel.querySelector('#fetchSystem').addEventListener('click', ()=> fetchOne('system'));
  panel.querySelector('#fetchWoo').addEventListener('click', ()=> fetchOne('woo'));
  panel.querySelector('#copySystem').addEventListener('click', ()=> copyToClipboard(sysPre.textContent || ''));
  panel.querySelector('#copyWoo').addEventListener('click', ()=> copyToClipboard(wooPre.textContent || ''));
}


