import { API_BASE } from '../config.js';
import { language } from '../core/language.js';

export async function searchProducts(q, { page=1, size=6, filters={}, sort=[] } = {}) {
  const res = await fetch(API_BASE + '/search', {
    method: 'POST',
    headers: { 'Content-Type':'application/json' },
    body: JSON.stringify({ q, page, size, filters, sort, lang: language.getLanguage() })
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // { items: [...] }
}

export async function getProduct(id) {
  const lang = language.getLanguage();
  const url = `${API_BASE}/products/${encodeURIComponent(id)}?lang=${lang}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json();
}


