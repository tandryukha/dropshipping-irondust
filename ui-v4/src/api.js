export async function searchProducts(q, { page = 1, size = 32, filters = {}, sort = null } = {}) {
  const fn = (window.apiV4 && window.apiV4.searchProducts) || (window.apiV3 && window.apiV3.searchProducts);
  if (!fn) throw new Error('API not available (apiV4/apiV3 not loaded)');
  return await fn(q, { page, size, filters, sort });
}


