import { useEffect, useMemo, useState } from 'react'

// Label helper functions for human-readable display
function titleCase(str) {
  if (!str) return '';
  return str.split(/[\s-]+/)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

function getBrandLabel(slug, brandNameMap) {
  // Try to get brand name from results if available, fallback to Title Case
  if (brandNameMap && brandNameMap[slug]) {
    return brandNameMap[slug];
  }
  return titleCase(slug);
}

function getCategoryLabel(slug) {
  if (!slug) return '';
  // Handle slashes - get last segment
  const parts = slug.split('/');
  const lastSegment = parts[parts.length - 1] || slug;
  return titleCase(lastSegment);
}

function getFormLabel(value) {
  return titleCase(value);
}

function getDietLabel(value) {
  return titleCase(value);
}

export function FiltersModal({ open, onClose, onClear, onApply, results, filters, onToggle, onToggleAvailability, onToggleOnSale }){
  const [activeIdx, setActiveIdx] = useState(0);
  useEffect(() => {
    if (open) document.body.style.overflow = 'hidden'; else document.body.style.overflow = '';
    return () => { document.body.style.overflow = ''; };
  }, [open]);

  const sections = [
    { title: 'Category', key: 'category' },
    { title: 'Brand', key: 'brand' },
    { title: 'Price', key: 'price' },
    { title: 'Deals', key: 'deals' },
    { title: 'Flavour', key: 'flavour' },
    { title: 'Dietary', key: 'diet' },
    { title: 'Item Form', key: 'form' },
    { title: 'Availability', key: 'availability' },
  ];

  const facetMaps = useMemo(()=>({
    brand: results?.facets?.brand_slug || {},
    category: results?.facets?.categories_slugs || {},
    form: results?.facets?.form || {},
    diet: results?.facets?.diet_tags || {},
  }), [results]);

  const flavourValues = useMemo(()=>{
    try {
      const set = new Set();
      (results?.items||[]).forEach(p => {
        const arr = p?.dynamic_attrs?.flavors; if(Array.isArray(arr)) arr.forEach(v=>{ if(v) set.add(v); });
      });
      return Array.from(set);
    } catch(e){ return []; }
  }, [results]);
  
  // Build brand name map from results
  const brandNameMap = useMemo(() => {
    const map = {};
    (results?.items || []).forEach(item => {
      if (item?.brand_slug && item?.brand) {
        map[item.brand_slug] = item.brand;
      }
    });
    return map;
  }, [results]);

  return (
    <div className={`filters-modal${open ? ' active' : ''}`} id="filtersModal" onClick={onClose}>
      <div className="filters-modal-content" onClick={(e)=>e.stopPropagation()}>
        <div className="filters-modal-header">
          <h2>Filters ⌵</h2>
          <button className="filters-modal-close" id="closeFilters" onClick={onClose}>✕</button>
        </div>
        <div className="filters-modal-body" id="filtersModalBody">
          <div className="filters-split">
            <div className="filters-nav" id="filtersNav">
              {sections.map((s, idx) => (
                <div key={s.key} className={"item" + (idx===activeIdx?" active":"")} data-target={"msec-"+idx} onClick={()=>setActiveIdx(idx)}>
                  {s.title}
                </div>
              ))}
            </div>
            <div className="filters-content" id="filtersContent">
              {sections.map((s, idx) => (
                <div key={s.key} className={"panel" + (idx===activeIdx?" active":"")} data-key={s.key}>
                  <div className="h"><span>{s.title}</span></div>
                  {s.key==='availability' ? (
                    <div id="fAvailability">
                      <button 
                        className={"chip" + (filters?.available===null?" active":"")} 
                        aria-pressed={filters?.available===null}
                        onClick={(e)=>{ e.preventDefault(); onToggleAvailability && onToggleAvailability(); }}
                      >
                        Include Out of Stock
                      </button>
                    </div>
                  ) : s.key==='deals' ? (
                    <div id="fDeals">
                      <button 
                        className={"chip" + (filters?.onSale===true?" active":"")} 
                        aria-pressed={filters?.onSale===true}
                        onClick={(e)=>{ e.preventDefault(); onToggleOnSale && onToggleOnSale(); }}
                      >
                        On sale
                      </button>
                    </div>
                  ) : s.key==='flavour' ? (
                    <div className="chips">
                      {flavourValues.map(val => (
                        <button 
                          key={val} 
                          type="button"
                          className={"chip" + (filters?.flavour?.has(val)?" active":"")} 
                          aria-pressed={filters?.flavour?.has(val)}
                          onClick={()=>onToggle && onToggle('flavour', val)}
                        >
                          {titleCase(val)}
                        </button>
                      ))}
                    </div>
                  ) : s.key==='brand' ? (
                    <div className="chips">
                      {Object.entries(facetMaps.brand || {}).sort((a,b)=>b[1]-a[1]).slice(0, 20).map(([val, cnt]) => (
                        <button 
                          key={val} 
                          type="button" 
                          className={"chip" + (filters?.brand?.has(val)?" active":"")} 
                          aria-pressed={filters?.brand?.has(val)}
                          onClick={()=>onToggle && onToggle('brand', val)}
                        >
                          {getBrandLabel(val, brandNameMap)} ({cnt})
                        </button>
                      ))}
                    </div>
                  ) : s.key==='category' ? (
                    <div className="chips">
                      {Object.entries(facetMaps.category || {}).sort((a,b)=>b[1]-a[1]).slice(0, 8).map(([val, cnt]) => (
                        <button 
                          key={val} 
                          type="button" 
                          className={"chip" + (filters?.category?.has(val)?" active":"")} 
                          aria-pressed={filters?.category?.has(val)}
                          onClick={()=>onToggle && onToggle('category', val)}
                        >
                          {getCategoryLabel(val)} ({cnt})
                        </button>
                      ))}
                    </div>
                  ) : s.key==='form' ? (
                    <div className="chips">
                      {Object.entries(facetMaps.form || {}).sort((a,b)=>b[1]-a[1]).slice(0, 8).map(([val, cnt]) => (
                        <button 
                          key={val} 
                          type="button" 
                          className={"chip" + (filters?.form?.has(val)?" active":"")} 
                          aria-pressed={filters?.form?.has(val)}
                          onClick={()=>onToggle && onToggle('form', val)}
                        >
                          {getFormLabel(val)} ({cnt})
                        </button>
                      ))}
                    </div>
                  ) : s.key==='diet' ? (
                    <div className="chips">
                      {Object.entries(facetMaps.diet || {}).sort((a,b)=>b[1]-a[1]).slice(0, 12).map(([val, cnt]) => (
                        <button 
                          key={val} 
                          type="button" 
                          className={"chip" + (filters?.diet?.has(val)?" active":"")} 
                          aria-pressed={filters?.diet?.has(val)}
                          onClick={()=>onToggle && onToggle('diet', val)}
                        >
                          {getDietLabel(val)} ({cnt})
                        </button>
                      ))}
                    </div>
                  ) : (
                    <div className="chips"></div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
        <div className="filters-modal-footer">
          <button className="btn" id="clearFilters" onClick={onClear}>Clear all</button>
          <button className="btn primary" id="applyFilters" onClick={onApply}>Show {(results?.total||0)} results</button>
        </div>
      </div>
    </div>
  );
}


