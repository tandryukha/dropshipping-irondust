(function(){
  const API_BASE = window.API_BASE || 'http://localhost:4000';

  function searchProducts(q, { page=1, size=32, filters={}, sort } = {}){
    const hasQuery = typeof q === 'string' && q.trim().length >= 2;
    const hasSort = Array.isArray(sort) && sort.length > 0;
    const endpoint = hasQuery && !hasSort ? '/search/hybrid' : '/search';
    const payload = { q, page, size, filters };
    if (hasSort) payload.sort = sort;
    return fetch(API_BASE + endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(res => {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.json(); // { items, total, facets }
    });
  }

  window.apiV3 = Object.assign({}, window.apiV3, { searchProducts });
})();


