export function Toolbar({ total, query, sort, setSort, openFilters }){
  return (
    <div className="toolbar">
      <div className="left">
        <div id="count">{(total||0)} results{query?` for ${query}`:''}</div>
        <div className="small muted meta" id="meta">Fast shipping • Free returns</div>
      </div>
      <div className="right">
        <button className="mobile-filters-btn" id="openFilters" onClick={(e)=>{ e.preventDefault(); e.stopPropagation(); openFilters(); }}>
          <span>☰</span>
          <span>Filters ⌵</span>
          <span className="badge" id="filtersBadge">0</span>
        </button>
        <span className="sort-label">Sort by:</span>
        <select id="sort" value={sort} onChange={(e)=>setSort(e.target.value)}>
          <option value="relevance">Featured</option>
          <option value="price-asc">Price: Low to High</option>
          <option value="price-desc">Price: High to Low</option>
          <option value="rating-desc">Avg. Customer Review</option>
          <option value="newest">Newest</option>
        </select>
      </div>
    </div>
  );
}


