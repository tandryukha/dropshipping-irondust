import { useEffect, useMemo, useRef, useState } from 'react'
import { Topbar } from './components/Topbar'
import { Toolbar } from './components/Toolbar'
import { Results } from './components/Results'
import { FiltersModal } from './components/FiltersModal'
import { BasketSidebar } from './components/BasketSidebar'

function assembleApiFiltersFromUi(state){
  const f = {};
  if (state.filters.available === null) f.in_stock = [true, false]; else f.in_stock = true;
  if (state.filters.brand && state.filters.brand.size) f.brand_slug = Array.from(state.filters.brand).map(slugifyLabelToSlug);
  if (state.filters.form && state.filters.form.size) f.form = Array.from(state.filters.form);
  if (state.filters.diet && state.filters.diet.size) f.diet_tags = Array.from(state.filters.diet).map(v => String(v).toLowerCase());
  if (state.filters.flavour && state.filters.flavour.size) f.flavor = Array.from(state.filters.flavour);
  if (state.filters.category && state.filters.category.size) f.categories_slugs = Array.from(state.filters.category).map(slugifyLabelToSlug);
  // Only include price bounds if user changed from defaults or picked a quick chip
  const DEFAULT_MIN = 4, DEFAULT_MAX = 390;
  const userChangedSlider = (Number.isFinite(state.filters.priceMin) && state.filters.priceMin !== DEFAULT_MIN) || (Number.isFinite(state.filters.priceMax) && state.filters.priceMax !== DEFAULT_MAX);
  if (userChangedSlider) {
    if (Number.isFinite(state.filters.priceMin)) f.price_min = { op: ">=", value: state.filters.priceMin };
    if (Number.isFinite(state.filters.priceMax)) f.price_max = { op: "<=", value: state.filters.priceMax };
  }
  if (state.filters.price){
    const parts = String(state.filters.price).split('-');
    const lo = parts[0] ? Number(parts[0]) : null;
    const hi = parts[1] ? Number(parts[1]) : null;
    if (Number.isFinite(lo)) f.price_min = { op: ">=", value: lo };
    if (Number.isFinite(hi)) f.price_max = { op: "<=", value: hi };
  }
  return f;
}
function slugifyLabelToSlug(s){
  if (!s) return s;
  const ascii = s.normalize('NFKD').replace(/[^\w\s-]/g,'');
  return ascii.trim().toLowerCase().replace(/\s+/g,'-');
}
function assembleRealSort(val){
  if (val === 'price-asc') return ["price:asc"];
  if (val === 'price-desc') return ["price:desc"];
  if (val === 'rating-desc') return ["rating:desc","review_count:desc"];
  return null;
}

export default function App() {
  const [q, setQ] = useState('');
  const [sort, setSort] = useState('relevance');
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState({ category:new Set(), brand:new Set(), flavour:new Set(), diet:new Set(), form:new Set(), container:new Set(), size:new Set(), price:null, priceMin:4, priceMax:390, ratingMin:null, available:true });
  const [results, setResults] = useState({ items: [], total: 0, facets: {} });
  const [filtersOpen, setFiltersOpen] = useState(false);

  const apiFilters = useMemo(()=>assembleApiFiltersFromUi({ filters }), [filters]);
  const apiSort = useMemo(()=>assembleRealSort(sort), [sort]);

  const lastReqRef = useRef('');
  useEffect(() => {
    const key = JSON.stringify({ q, page, filters: apiFilters, sort: apiSort });
    if (key === lastReqRef.current) return;
    lastReqRef.current = key;
    fetchResults(q, page, apiFilters, apiSort);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, page, apiFilters, apiSort]);

  async function fetchResults(qIn, pageIn, filtersIn, sortIn){
    try {
      const fn = (window.apiV4 && window.apiV4.searchProducts) || (window.apiV3 && window.apiV3.searchProducts);
      if (!fn) return;
      const resp = await fn(qIn, { page: pageIn, size: 32, filters: filtersIn, sort: sortIn });
      if (resp && Array.isArray(resp.items)) setResults(resp);
    } catch(e){
      // console error is okay in dev
      console.error('search failed', e);
    }
  }

  function onSearch(){
    setPage(1);
  }

  function openFiltersModal(){ setFiltersOpen(true); }
  function closeFiltersModal(){ setFiltersOpen(false); }

  function fmtPriceEu(n, cents){
    const v = (typeof n === 'number' && !isNaN(n)) ? n : (typeof cents === 'number' ? (cents/100) : null);
    if (v == null) return null;
    const s = v.toFixed(2);
    const i = s.indexOf('.');
    return { whole: s.slice(0, i), fraction: s.slice(i+1) };
  }

  return (
    <>
      <Topbar q={q} setQ={setQ} onSearch={onSearch} />

      <div className="sugg" id="sugg" style={{display:'none'}}>
        <div className="box" id="suggBox"></div>
      </div>

      <div className="mobile-filterbar" id="mobileFilterbar">
        <div className="mf-scroll">
          <button className="mf-chip" data-range="0-20">Up to 20 EUR</button>
          <button className="mf-chip" data-range="20-30">20 - 30 EUR</button>
          <button className="mf-chip" data-range="30-35">30 - 35 EUR</button>
          <button className="mf-chip" data-range="35-">Over 35 EUR</button>
        </div>
        <div className="mf-divider"></div>
        <span className="mf-right" id="openFilters2" onClick={openFiltersModal}>Filters ⌵</span>
      </div>

      <Toolbar total={results.total} query={q} sort={sort} setSort={setSort} openFilters={openFiltersModal} />

      <div className="shell">
        <aside className="filters">
          <div className="panel">
            <div className="h">
              <div>Refine</div>
              <button className="link" id="clearAll">Clear all</button>
            </div>
            <div className="chips" id="activePills"></div>
          </div>

          <div className="panel" data-key="category">
            <div className="h"><span>Category</span></div>
            <div id="fCategory"></div>
          </div>
          <div className="panel" data-key="brand">
            <div className="h"><span>Brand</span></div>
            <div id="fBrand"></div>
            <button className="link" id="brandMore">Show more</button>
          </div>
          <div className="panel" data-key="price">
            <div className="h"><span>Price</span></div>
            <div>
              <div className="price-range">
                <div className="price-display" id="priceDisplay">€4–€390+</div>
                <div className="price-slider-container">
                  <div className="price-slider-wrapper">
                    <div className="price-slider" id="priceSlider">
                      <div className="price-slider-track" id="priceTrack"></div>
                      <div className="price-slider-thumb" id="priceThumbMin" data-thumb="min"></div>
                      <div className="price-slider-thumb" id="priceThumbMax" data-thumb="max"></div>
                    </div>
                  </div>
                  <button className="price-go-btn" id="priceGo">Go</button>
                </div>
              </div>
              <div className="chips">
                <div className="chip" data-val="0-20">€0–20</div>
                <div className="chip" data-val="20-40">€20–40</div>
                <div className="chip" data-val="40-80">€40–80</div>
                <div className="chip" data-val="80-">€80+</div>
              </div>
            </div>
          </div>
          <div className="panel" data-key="flavour">
            <div className="h"><span>Flavour</span></div>
            <div id="fFlavour"></div>
          </div>
          <div className="panel" data-key="diet">
            <div className="h"><span>Dietary</span></div>
            <div id="fDiet"></div>
          </div>
          <div className="panel" data-key="form">
            <div className="h"><span>Item Form</span></div>
            <div id="fForm"></div>
            <button className="link" id="formMore">See Less</button>
          </div>
          <div className="panel" data-key="container">
            <div className="h"><span>Container Type</span></div>
            <div id="fContainer"></div>
          </div>
          <div className="panel" data-key="size">
            <div className="h"><span>Size</span></div>
            <div id="fSize"></div>
          </div>
          <div className="panel" data-key="availability">
            <div className="h"><span>Availability</span></div>
            <div id="fAvailability">
              <button className="chip" id="availToggle" type="button">Include Out of Stock</button>
            </div>
          </div>
        </aside>

        <main className="main">
          <div className="active-pills" id="pills"></div>
          <Results items={results.items} />
          <div className="pager" id="pager"></div>
          <div className="panel" id="relatedPanel" style={{display:'none', marginTop: 8}}>
            <div className="h">Related searches</div>
            <div className="chips" id="relatedChips"></div>
          </div>

          <div className="panel" id="browsing-history" style={{marginTop: 16}}>
            <div className="h" style={{marginBottom: 12}}>Inspired by your browsing history</div>
            <div className="carousel-wrapper">
              <button className="carousel-nav prev" id="carouselPrev" disabled>‹</button>
              <div className="carousel" id="historyCarousel"></div>
              <button className="carousel-nav next" id="carouselNext">›</button>
            </div>
            <div style={{textAlign: 'right', marginTop: 8}}>
              <span style={{fontSize: 12, color: 'var(--muted)'}} id="carouselPage">Page 1 of 2</span>
            </div>
          </div>
        </main>

        <BasketSidebar />
      </div>

      <FiltersModal 
        open={filtersOpen}
        onClose={closeFiltersModal}
        onClear={()=>{ setFilters({ category:new Set(), brand:new Set(), flavour:new Set(), diet:new Set(), form:new Set(), container:new Set(), size:new Set(), price:null, priceMin:4, priceMax:390, ratingMin:null, available:true }); }}
        onApply={()=>{ closeFiltersModal(); fetchResults(q, 1, apiFilters, apiSort); }}
      />
      {/* end */}

      <div className="location-modal" id="locationModal" aria-hidden="true">
        <div className="location-modal-content" role="dialog" aria-modal="true" aria-labelledby="locTitle">
          <button className="floating-done" id="closeLocationTop">DONE</button>
          <div className="location-modal-header">
            <div className="title" id="locTitle">Choose your location</div>
          </div>
          <div className="location-modal-body">
            <div className="desc">Delivery options and delivery speeds may vary for different locations</div>
            <button className="signin">Sign in to see your addresses</button>
            <div className="divider"></div>
            <div className="actions">
              <button className="row"><span className="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 21c-4.6-4.9-7-8.2-7-11.2a7 7 0 1 1 14 0c0 3-2.4 6.3-7 11.2z"></path><circle cx="12" cy="10" r="3"></circle></svg></span>Enter a postal code</button>
              <button className="row"><span className="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 5v2"></path><path d="M12 17v2"></path><path d="M5 12h2"></path><path d="M17 12h2"></path><circle cx="12" cy="12" r="4"></circle></svg></span>Use my current location</button>
              <button className="row" id="deliverOutsideBtn"><span className="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="9"></circle></svg></span>Deliver outside Estonia</button>
            </div>
          </div>
        </div>
      </div>

      <div id="ariaLive" aria-live="polite" style={{position:'absolute',left:-9999,width:1,height:1,overflow:'hidden'}}></div>
    </>
  );
}
