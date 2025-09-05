// Minimal shared state with a notify() helper
import { bus } from './bus.js';

export const store = {
  cartCount: 2,
  cartSubtotalCents: 0,
  cartItems: [],
  pdpQty: 1,
  locale: 'EN',
  set(key, val) { this[key] = val; bus.dispatchEvent(new CustomEvent('store:'+key, { detail: val })); },
  get(key) { return this[key]; }
};


