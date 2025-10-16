import { createContext, useContext, useMemo, useReducer } from 'react'

const initialState = {
  query: '',
  sort: 'relevance',
  page: 1,
  filters: {
    category: new Set(),
    brand: new Set(),
    flavour: new Set(),
    diet: new Set(),
    form: new Set(),
    container: new Set(),
    size: new Set(),
    price: null,
    priceMin: 4,
    priceMax: 390,
    ratingMin: null,
    available: true
  },
  results: { items: [], total: 0, facets: {} },
  basket: new Map(),
  ui: { isBasketOpen: false, isFiltersModalOpen: false, isLocationModalOpen: false }
};

function reducer(state, action){
  switch(action.type){
    case 'setQuery': return { ...state, query: action.query, page: 1 };
    case 'setSort': return { ...state, sort: action.sort, page: 1 };
    case 'setPage': return { ...state, page: action.page };
    case 'setResults': return { ...state, results: action.results };
    case 'replaceFilters': return { ...state, filters: action.filters, page: 1 };
    case 'updateFilters': return { ...state, filters: action.update(state.filters), page: 1 };
    case 'basketAdd': {
      const b = new Map(state.basket); b.set(action.id, (b.get(action.id)||0)+1);
      return { ...state, basket: b, ui: { ...state.ui, isBasketOpen: true } };
    }
    case 'basketRemove': {
      const b = new Map(state.basket); b.delete(action.id);
      return { ...state, basket: b };
    }
    case 'toggleBasket': return { ...state, ui: { ...state.ui, isBasketOpen: !state.ui.isBasketOpen } };
    case 'openFilters': return { ...state, ui: { ...state.ui, isFiltersModalOpen: true } };
    case 'closeFilters': return { ...state, ui: { ...state.ui, isFiltersModalOpen: false } };
    case 'openLocation': return { ...state, ui: { ...state.ui, isLocationModalOpen: true } };
    case 'closeLocation': return { ...state, ui: { ...state.ui, isLocationModalOpen: false } };
    default: return state;
  }
}

const StoreContext = createContext(null);

export function StoreProvider({ children }){
  const [state, dispatch] = useReducer(reducer, initialState);
  const value = useMemo(()=>({ state, dispatch }), [state]);
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore(){
  const ctx = useContext(StoreContext);
  if(!ctx) throw new Error('useStore must be used within StoreProvider');
  return ctx;
}


