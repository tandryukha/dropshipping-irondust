import { API_BASE } from '../config.js';

export async function searchProducts(q, { page=1, size=6, filters={}, sort=[] } = {}) {
  const res = await fetch(API_BASE + '/search', {
    method: 'POST',
    headers: { 'Content-Type':'application/json' },
    body: JSON.stringify({ q, page, size, filters, sort })
  });
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json(); // { items: [...] }
}

export async function getProduct(id) {
  const res = await fetch(API_BASE + '/products/' + encodeURIComponent(id));
  if (!res.ok) throw new Error('HTTP '+res.status);
  return res.json();
}


