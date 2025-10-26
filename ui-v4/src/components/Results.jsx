export function ProductCard({ p, onAdd }){
  const priceParts = fmtPriceEu(p.price, p.price_cents);
  const brand = p.brand_name || p.brand_slug || '';
  const cat = Array.isArray(p.categories_names) && p.categories_names.length ? p.categories_names[0] : '';
  const title = (p.display_title && p.display_title.trim()) ? p.display_title : (p.name || '');
  const firstImg = Array.isArray(p.images) && p.images.length ? p.images[0] : null;
  return (
    <div className="product" data-id={String(p.id)}>
      <div className="thumb">{firstImg ? <img src={firstImg} alt="" style={{maxWidth:'100%',maxHeight:'100%',objectFit:'contain'}} /> : 'IMG'}</div>
      <div className="p-title">{title}</div>
      <div className="small"><span className="p-brand">{brand}</span>{cat ? <> • <span>{cat}</span></> : null}</div>
      <div className="small"><span className="p-stars">{'★★★★★'.slice(0, Math.round(p.rating || 0))}</span> <span className="muted">({p.review_count||0})</span></div>
      {priceParts ? <div className="price"><span className="currency">€</span><span className="whole">{priceParts.whole}</span><span className="fraction">{priceParts.fraction}</span></div> : null}
      <div className="p-cta">
        <button className="btn small add" onClick={()=>onAdd && onAdd(String(p.id))}>Add to Basket</button>
      </div>
    </div>
  );
}

import { useStore } from '../store.jsx'

export function Results({ items, onAdd }){
  const { dispatch } = useStore();
  return (
    <div className="grid" id="grid">
      {(items||[]).map(p => (
        <ProductCard key={String(p.id)} p={p} onAdd={onAdd || ((id)=>dispatch({ type: 'basketAdd', id: p.id, product: p }))} />
      ))}
    </div>
  );
}

function fmtPriceEu(n, cents){
  const v = (typeof n === 'number' && !isNaN(n)) ? n : (typeof cents === 'number' ? (cents/100) : null);
  if (v == null) return null;
  const s = v.toFixed(2);
  const i = s.indexOf('.');
  return { whole: s.slice(0, i), fraction: s.slice(i+1) };
}


