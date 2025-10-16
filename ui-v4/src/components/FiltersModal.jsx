import { useEffect, useMemo, useState } from 'react'

export function FiltersModal({ open, onClose, onClear, onApply, results, filters, onToggle, onToggleAvailability }){
  const [activeIdx, setActiveIdx] = useState(0);
  useEffect(() => {
    if (open) document.body.style.overflow = 'hidden'; else document.body.style.overflow = '';
    return () => { document.body.style.overflow = ''; };
  }, [open]);

  const sections = [
    { title: 'Category', key: 'category' },
    { title: 'Brand', key: 'brand' },
    { title: 'Price', key: 'price' },
    { title: 'Flavour', key: 'flavour' },
    { title: 'Dietary', key: 'diet' },
    { title: 'Item Form', key: 'form' },
    { title: 'Container Type', key: 'container' },
    { title: 'Size', key: 'size' },
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
                      <button className={"chip" + (filters?.available===null?" active":"")} onClick={(e)=>{ e.preventDefault(); onToggleAvailability && onToggleAvailability(); }}>Include Out of Stock</button>
                    </div>
                  ) : s.key==='flavour' ? (
                    <div className="chips">
                      {flavourValues.map(val => (
                        <div key={val} className={"chip" + (filters?.flavour?.has(val)?" active":"")} onClick={()=>onToggle && onToggle('flavour', val)}>{val}</div>
                      ))}
                    </div>
                  ) : s.key==='brand' || s.key==='category' || s.key==='form' || s.key==='diet' ? (
                    <div className="chips">
                      {Object.entries(facetMaps[s.key] || {}).sort((a,b)=>b[1]-a[1]).slice(0, s.key==='diet'?12: (s.key==='brand'?20:8)).map(([val, cnt]) => (
                        <button key={val} type="button" className={"chip" + (filters?.[s.key]?.has(val)?" active":"")} onClick={()=>onToggle && onToggle(s.key, val)}>{val} ({cnt})</button>
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


