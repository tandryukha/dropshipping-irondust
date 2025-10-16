export function FiltersModal({ open, onClose, onClear, onApply }){
  return (
    <div className={`filters-modal${open ? ' active' : ''}`} id="filtersModal" onClick={onClose}>
      <div className="filters-modal-content" onClick={(e)=>e.stopPropagation()}>
        <div className="filters-modal-header">
          <h2>Filters ⌵</h2>
          <button className="filters-modal-close" id="closeFilters" onClick={onClose}>✕</button>
        </div>
        <div className="filters-modal-body" id="filtersModalBody"></div>
        <div className="filters-modal-footer">
          <button className="btn" id="clearFilters" onClick={onClear}>Clear all</button>
          <button className="btn primary" id="applyFilters" onClick={onApply}>Apply</button>
        </div>
      </div>
    </div>
  );
}


