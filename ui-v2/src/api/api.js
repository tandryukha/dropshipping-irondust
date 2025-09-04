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

export async function getAlternatives(id, { limit=8 } = {}) {
  const lang = language.getLanguage();
  const url = `${API_BASE}/products/${encodeURIComponent(id)}/alternatives?limit=${encodeURIComponent(limit)}&lang=${lang}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // { items: [...], total }
}

export async function getComplements(id, { limit=8 } = {}) {
  const lang = language.getLanguage();
  const url = `${API_BASE}/products/${encodeURIComponent(id)}/complements?limit=${encodeURIComponent(limit)}&lang=${lang}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json();
}


export async function getFeatureFlag(name, { defaultValue=false } = {}){
  const url = `${API_BASE}/feature-flags/${encodeURIComponent(name)}?defaultValue=${String(defaultValue)}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // { name, enabled }
}

export async function getAiAnswer(q, { page=1, size=6, filters={} } = {}){
  const lang = language.getLanguage();
  const payload = { q, page, size, filters, lang };
  const res = await fetch(API_BASE + '/search/ai', {
    method: 'POST',
    headers: { 'Content-Type':'application/json' },
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // { answer, items: [{ id, name, price_cents, price_per_serving, permalink }] }
}

export async function searchContent(q, { page=1, size=6, filter } = {}) {
  const payload = { q, page, size };
  if (typeof filter === 'string' && filter.trim()) payload.filter = filter;
  const res = await fetch(API_BASE + '/content/search', {
    method: 'POST',
    headers: { 'Content-Type':'application/json' },
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // Meili raw response: { hits, totalHits, facets, ... }
}


