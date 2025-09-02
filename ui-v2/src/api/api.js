import { API_BASE } from '../config.js';
import { language } from '../core/language.js';

export async function searchProducts(q, { page=1, size=6, filters={}, sort } = {}) {
  const lang = language.getLanguage();
  const hasQuery = typeof q === 'string' && q.trim().length >= 2;
  const hasSort = Array.isArray(sort) && sort.length > 0;
  // Use hybrid for natural-language relevance queries (no explicit sort)
  const endpoint = hasQuery && !hasSort ? '/search/hybrid' : '/search';
  const payload = { q, page, size, filters, lang };
  if (hasSort) payload.sort = sort;

  const res = await fetch(API_BASE + endpoint, {
    method: 'POST',
    headers: { 'Content-Type':'application/json' },
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // { items, total, facets }
}

export async function getProduct(id) {
  const lang = language.getLanguage();
  const url = `${API_BASE}/products/${encodeURIComponent(id)}?lang=${lang}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json();
}


