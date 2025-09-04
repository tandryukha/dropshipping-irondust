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

export async function renderContent(hit) {
  const res = await fetch(API_BASE + '/content/render', {
    method: 'POST',
    headers: { 'Content-Type':'application/json' },
    body: JSON.stringify({ hit })
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // { html, meta }
}


// Admin helpers
export async function ingestProducts(ids, { clearAi=false, clearTranslation=false, adminKey } = {}) {
  if (!Array.isArray(ids)) throw new Error('ids must be an array');
  const headers = { 'Content-Type':'application/json' };
  const key = adminKey || localStorage.getItem('adminKey') || 'dev_admin_key';
  if (key) headers['x-admin-key'] = key;
  if (clearAi) headers['x-clear-ai-cache'] = 'true';
  if (clearTranslation) headers['x-clear-translation-cache'] = 'true';
  const res = await fetch(API_BASE + '/ingest/products', {
    method: 'POST',
    headers,
    body: JSON.stringify({ ids })
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // Ingest report
}

export async function getAdminRawSystem(id, { adminKey } = {}) {
  const key = adminKey || localStorage.getItem('adminKey') || 'dev_admin_key';
  const res = await fetch(`${API_BASE}/admin/raw/system/${encodeURIComponent(id)}`, {
    headers: key ? { 'x-admin-key': key } : {}
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json();
}

export async function getAdminRawWoo(id, { adminKey } = {}) {
  const key = adminKey || localStorage.getItem('adminKey') || 'dev_admin_key';
  const res = await fetch(`${API_BASE}/admin/raw/woo/${encodeURIComponent(id)}`, {
    headers: key ? { 'x-admin-key': key } : {}
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json();
}

